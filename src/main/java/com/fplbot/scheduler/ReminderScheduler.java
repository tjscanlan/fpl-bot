package com.fplbot.scheduler;

import com.fplbot.reminders.ReminderService;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

  private final ReminderService reminderService;
  private final ScheduledExecutorService executor;

  public ReminderScheduler(ReminderService reminderService) {
    this.reminderService = reminderService;
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "reminder-scheduler");
              thread.setDaemon(true);
              return thread;
            });
  }

  public void start(Duration pollInterval) {
    executor.scheduleAtFixedRate(
        this::checkAndSendReminder, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void stop() {
    executor.shutdownNow();
  }

  private void checkAndSendReminder() {
    // scheduleAtFixedRate silently stops future runs if a task throws, so every
    // exception must be swallowed here rather than left to propagate.
    try {
      reminderService.checkAndSendReminder();
    } catch (Exception e) {
      log.error("Failed to check/send deadline reminder", e);
    }
  }
}
