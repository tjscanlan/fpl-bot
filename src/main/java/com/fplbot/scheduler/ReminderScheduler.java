package com.fplbot.scheduler;

import com.fplbot.fplapi.FplApiClient;
import com.fplbot.fplapi.Gameweek;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

  private final FplApiClient fplApiClient;
  private final ScheduledExecutorService executor;

  public ReminderScheduler(FplApiClient fplApiClient) {
    this.fplApiClient = fplApiClient;
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
        this::checkForUpcomingDeadline, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void stop() {
    executor.shutdownNow();
  }

  private void checkForUpcomingDeadline() {
    // scheduleAtFixedRate silently stops future runs if a task throws, so every
    // exception must be swallowed here rather than left to propagate.
    try {
      Optional<Gameweek> nextDeadline = fplApiClient.getNextDeadline();
      if (nextDeadline.isPresent()) {
        Gameweek gameweek = nextDeadline.get();
        log.info("Next deadline: {} at {}", gameweek.name(), gameweek.deadlineTime());
      } else {
        log.warn("No upcoming gameweek deadline found");
      }
    } catch (Exception e) {
      log.error("Failed to check for upcoming deadline", e);
    }
  }
}
