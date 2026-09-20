package com.fplbot.scheduler;

import com.fplbot.prices.PriceChangeService;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceCheckScheduler {

  private static final Logger log = LoggerFactory.getLogger(PriceCheckScheduler.class);
  private static final ZoneId UK_ZONE = ZoneId.of("Europe/London");

  private final PriceChangeService priceChangeService;
  private final LocalTime runAt;
  private final ScheduledExecutorService executor;

  public PriceCheckScheduler(PriceChangeService priceChangeService, LocalTime runAt) {
    this.priceChangeService = priceChangeService;
    this.runAt = runAt;
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "price-check-scheduler");
              thread.setDaemon(true);
              return thread;
            });
  }

  public void start() {
    Duration initialDelay = durationUntilNextRun();
    executor.scheduleAtFixedRate(
        this::checkPrices,
        initialDelay.toMillis(),
        Duration.ofDays(1).toMillis(),
        TimeUnit.MILLISECONDS);
    log.info(
        "Price-check job scheduled for {} {} daily, first run in {}", runAt, UK_ZONE, initialDelay);
  }

  public void stop() {
    executor.shutdownNow();
  }

  private Duration durationUntilNextRun() {
    ZonedDateTime now = ZonedDateTime.now(UK_ZONE);
    ZonedDateTime nextRun = now.with(runAt);
    if (!nextRun.isAfter(now)) {
      nextRun = nextRun.plusDays(1);
    }
    return Duration.between(now, nextRun);
  }

  private void checkPrices() {
    // scheduleAtFixedRate silently stops future runs if a task throws, so every
    // exception must be swallowed here rather than left to propagate.
    try {
      priceChangeService.checkForPriceChanges();
    } catch (Exception e) {
      log.error("Price check failed", e);
    }
  }
}
