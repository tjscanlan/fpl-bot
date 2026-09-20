package com.fplbot.reminders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fplbot.fplapi.FplApiClient;
import com.fplbot.fplapi.Gameweek;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.Test;

class ReminderServiceTest {

  private static final long CHANNEL_ID = 42L;
  private static final Duration LEAD_TIME = Duration.ofMinutes(60);

  private final FplApiClient fplApiClient = mock(FplApiClient.class);
  private final ReminderRepository reminderRepository = mock(ReminderRepository.class);
  private final JDA jda = mock(JDA.class);
  private final MessageChannel channel = mock(MessageChannel.class);
  private final MessageCreateAction action = mock(MessageCreateAction.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private final ReminderService service =
      new ReminderService(fplApiClient, reminderRepository, jda, CHANNEL_ID, LEAD_TIME, registry);

  @Test
  void sendsReminderWhenWithinWindowAndNotAlreadySent() throws Exception {
    Gameweek gameweek = gameweekWithDeadline(Instant.now().plus(Duration.ofMinutes(30)));
    when(fplApiClient.getNextDeadline()).thenReturn(Optional.of(gameweek));
    when(reminderRepository.alreadySent(gameweek.id())).thenReturn(false);
    when(jda.getChannelById(MessageChannel.class, CHANNEL_ID)).thenReturn(channel);
    when(channel.sendMessage(anyString())).thenReturn(action);

    service.checkAndSendReminder();

    verify(action).complete();
    verify(reminderRepository).recordSent(gameweek.id());
    assertEquals(1, successCount());
    assertEquals(0, failureCount());
  }

  @Test
  void doesNotResendWhenAlreadySent() throws Exception {
    Gameweek gameweek = gameweekWithDeadline(Instant.now().plus(Duration.ofMinutes(30)));
    when(fplApiClient.getNextDeadline()).thenReturn(Optional.of(gameweek));
    when(reminderRepository.alreadySent(gameweek.id())).thenReturn(true);

    service.checkAndSendReminder();

    verify(channel, never()).sendMessage(anyString());
    verify(reminderRepository, never()).recordSent(gameweek.id());
    assertEquals(0, successCount());
    assertEquals(0, failureCount());
  }

  @Test
  void doesNotSendBeforeTheReminderWindowOpens() throws Exception {
    // Deadline is further away than the lead time, so it's too early to remind.
    Gameweek gameweek = gameweekWithDeadline(Instant.now().plus(Duration.ofMinutes(120)));
    when(fplApiClient.getNextDeadline()).thenReturn(Optional.of(gameweek));

    service.checkAndSendReminder();

    verify(reminderRepository, never()).alreadySent(gameweek.id());
    verify(channel, never()).sendMessage(anyString());
  }

  @Test
  void doesNotSendForAnAlreadyPassedDeadline() throws Exception {
    // A missed deadline is surfaced by alerting (deploy/prometheus alert rules), not
    // by sending a late reminder pretending it's still actionable.
    Gameweek gameweek = gameweekWithDeadline(Instant.now().minus(Duration.ofMinutes(10)));
    when(fplApiClient.getNextDeadline()).thenReturn(Optional.of(gameweek));

    service.checkAndSendReminder();

    verify(reminderRepository, never()).alreadySent(gameweek.id());
    verify(channel, never()).sendMessage(anyString());
  }

  @Test
  void doesNotRecordSentWhenDeliveryFailsSoTheNextPollRetries() throws Exception {
    Gameweek gameweek = gameweekWithDeadline(Instant.now().plus(Duration.ofMinutes(30)));
    when(fplApiClient.getNextDeadline()).thenReturn(Optional.of(gameweek));
    when(reminderRepository.alreadySent(gameweek.id())).thenReturn(false);
    when(jda.getChannelById(MessageChannel.class, CHANNEL_ID)).thenReturn(channel);
    when(channel.sendMessage(anyString())).thenReturn(action);
    when(action.complete()).thenThrow(new RuntimeException("Discord API unavailable"));

    assertThrows(RuntimeException.class, service::checkAndSendReminder);

    verify(reminderRepository, never()).recordSent(gameweek.id());
    assertEquals(0, successCount());
    assertEquals(1, failureCount());
  }

  private static Gameweek gameweekWithDeadline(Instant deadline) {
    return new Gameweek(1, "Gameweek 1", deadline, true, false);
  }

  private double successCount() {
    return registry.get("bot.reminder.delivery").tag("result", "success").counter().count();
  }

  private double failureCount() {
    return registry.get("bot.reminder.delivery").tag("result", "failure").counter().count();
  }
}
