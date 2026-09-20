package com.fplbot.commands;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class PingCommand extends ListenerAdapter {

  private final Timer latencyTimer;

  public PingCommand(MeterRegistry registry) {
    this.latencyTimer =
        Timer.builder("bot.ping.latency")
            .description("Latency of the /ping command from receipt to reply sent")
            .register(registry);
  }

  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    if (!event.getName().equals("ping")) {
      return;
    }
    Timer.Sample sample = Timer.start();
    event.reply("Pong!").queue(v -> sample.stop(latencyTimer));
  }
}
