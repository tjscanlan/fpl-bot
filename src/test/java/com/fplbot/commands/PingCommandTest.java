package com.fplbot.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.Consumer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PingCommandTest {

  @Test
  void repliesPongToPingCommand() {
    PingCommand command = new PingCommand(new SimpleMeterRegistry());
    SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    ReplyCallbackAction action = mock(ReplyCallbackAction.class);
    when(event.getName()).thenReturn("ping");
    when(event.reply("Pong!")).thenReturn(action);

    command.onSlashCommandInteraction(event);

    verify(event).reply("Pong!");
    verify(action).queue(any());
  }

  @Test
  void ignoresCommandsOtherThanPing() {
    PingCommand command = new PingCommand(new SimpleMeterRegistry());
    SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    when(event.getName()).thenReturn("other");

    command.onSlashCommandInteraction(event);

    verify(event, never()).reply(anyString());
  }

  @Test
  @SuppressWarnings("unchecked")
  void recordsLatencyOnceTheReplyCompletes() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PingCommand command = new PingCommand(registry);
    SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    ReplyCallbackAction action = mock(ReplyCallbackAction.class);
    when(event.getName()).thenReturn("ping");
    when(event.reply("Pong!")).thenReturn(action);

    command.onSlashCommandInteraction(event);

    ArgumentCaptor<Consumer<InteractionHook>> callback = ArgumentCaptor.forClass(Consumer.class);
    verify(action).queue(callback.capture());
    callback.getValue().accept(null);

    assertEquals(1, registry.get("bot.ping.latency").timer().count());
  }
}
