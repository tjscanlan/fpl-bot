package com.fplbot.reminders;

import com.fplbot.fplapi.FplApiClient;
import com.fplbot.fplapi.Gameweek;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReminderService {

  private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

  private final FplApiClient fplApiClient;
  private final ReminderRepository reminderRepository;
  private final JDA jda;
  private final long reminderChannelId;
  private final Duration leadTime;

  public ReminderService(
      FplApiClient fplApiClient,
      ReminderRepository reminderRepository,
      JDA jda,
      long reminderChannelId,
      Duration leadTime) {
    this.fplApiClient = fplApiClient;
    this.reminderRepository = reminderRepository;
    this.jda = jda;
    this.reminderChannelId = reminderChannelId;
    this.leadTime = leadTime;
  }

  /**
   * Only records a gameweek as sent after a confirmed delivery, so a failed send is retried on the
   * next poll rather than being silently skipped.
   */
  public void checkAndSendReminder() throws Exception {
    Optional<Gameweek> nextDeadline = fplApiClient.getNextDeadline();
    if (nextDeadline.isEmpty()) {
      log.warn("No upcoming gameweek deadline found");
      return;
    }

    Gameweek gameweek = nextDeadline.get();
    if (!isWithinReminderWindow(gameweek)) {
      return;
    }

    if (reminderRepository.alreadySent(gameweek.id())) {
      return;
    }

    sendReminder(gameweek);
    reminderRepository.recordSent(gameweek.id());
  }

  private boolean isWithinReminderWindow(Gameweek gameweek) {
    Instant now = Instant.now();
    Instant windowStart = gameweek.deadlineTime().minus(leadTime);
    return !now.isBefore(windowStart) && !now.isAfter(gameweek.deadlineTime());
  }

  private void sendReminder(Gameweek gameweek) {
    MessageChannel channel = jda.getChannelById(MessageChannel.class, reminderChannelId);
    if (channel == null) {
      throw new IllegalStateException("Reminder channel " + reminderChannelId + " not found");
    }
    channel
        .sendMessage(
            "⏰ **"
                + gameweek.name()
                + "** deadline is at "
                + gameweek.deadlineTime()
                + " — get your transfers in!")
        .complete();
    log.info("Sent deadline reminder for {}", gameweek.name());
  }
}
