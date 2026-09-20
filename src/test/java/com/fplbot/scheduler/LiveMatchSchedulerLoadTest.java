package com.fplbot.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fplbot.fplapi.Fixture;
import com.fplbot.fplapi.FplApiClient;
import com.fplbot.livematch.LiveMatchAlertSender;
import com.fplbot.livematch.LiveMatchTracker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Simulates a busy Saturday 3pm slate: several matches live and scoring at once, verifying the
 * per-match concurrent processing pipeline (item 32) detects and alerts on all of them
 * independently rather than serializing or cross-contaminating state between fixtures.
 */
class LiveMatchSchedulerLoadTest {

  private static final int CONCURRENT_MATCHES = 10;

  @Test
  void concurrentlyLiveMatchesAreAllDetectedAndAlertedIndependently() throws Exception {
    List<Fixture> initialFixtures = new ArrayList<>();
    List<Fixture> updatedFixtures = new ArrayList<>();
    for (int i = 0; i < CONCURRENT_MATCHES; i++) {
      initialFixtures.add(new Fixture(i, 1, i * 2, i * 2 + 1, 0, 0, true, false, false, 10));
      updatedFixtures.add(new Fixture(i, 1, i * 2, i * 2 + 1, 1, 0, true, false, false, 15));
    }

    FplApiClient fplApiClient = mock(FplApiClient.class);
    when(fplApiClient.getFixtures()).thenReturn(initialFixtures).thenReturn(updatedFixtures);
    when(fplApiClient.getTeams()).thenReturn(List.of());

    CountDownLatch allAlertsSent = new CountDownLatch(CONCURRENT_MATCHES);
    Set<Integer> alertedFixtureIds = ConcurrentHashMap.newKeySet();
    LiveMatchAlertSender liveMatchAlertSender = mock(LiveMatchAlertSender.class);
    doAnswer(
            invocation -> {
              Fixture fixture = invocation.getArgument(0);
              alertedFixtureIds.add(fixture.id());
              allAlertsSent.countDown();
              return null;
            })
        .when(liveMatchAlertSender)
        .sendScoreUpdate(any(Fixture.class), anyMap());

    LiveMatchScheduler scheduler =
        new LiveMatchScheduler(
            fplApiClient, new LiveMatchTracker(), liveMatchAlertSender, new SimpleMeterRegistry());

    // The first poll only seeds the tracker (no delta yet); the second, 50ms later,
    // sees every match's score change at once and should alert on all of them
    // concurrently.
    scheduler.start(Duration.ofMillis(50));
    try {
      assertTrue(allAlertsSent.await(5, TimeUnit.SECONDS), "not all matches were alerted in time");
    } finally {
      scheduler.stop();
    }

    assertEquals(CONCURRENT_MATCHES, alertedFixtureIds.size());
    for (int i = 0; i < CONCURRENT_MATCHES; i++) {
      assertTrue(alertedFixtureIds.contains(i), "fixture " + i + " was not alerted");
    }
  }
}
