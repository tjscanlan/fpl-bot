package com.fplbot.scheduler;

import com.fplbot.fplapi.Fixture;
import com.fplbot.fplapi.FplApiClient;
import com.fplbot.fplapi.Team;
import com.fplbot.livematch.LiveMatchAlertSender;
import com.fplbot.livematch.LiveMatchTracker;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls all fixtures once per cycle, then dispatches each currently-live match to its own task on a
 * separate pool so processing N concurrent matches doesn't serialize behind a single thread — with
 * several live matches at once (a Saturday 3pm slate), that would blow the freshness SLO for
 * matches processed later in the list.
 */
public class LiveMatchScheduler {

  private static final Logger log = LoggerFactory.getLogger(LiveMatchScheduler.class);
  private static final int MATCH_PROCESSING_POOL_SIZE = 10;

  private final FplApiClient fplApiClient;
  private final LiveMatchTracker liveMatchTracker;
  private final LiveMatchAlertSender liveMatchAlertSender;
  private final ScheduledExecutorService pollingExecutor;
  private final ExecutorService matchProcessingExecutor;

  public LiveMatchScheduler(
      FplApiClient fplApiClient,
      LiveMatchTracker liveMatchTracker,
      LiveMatchAlertSender liveMatchAlertSender) {
    this.fplApiClient = fplApiClient;
    this.liveMatchTracker = liveMatchTracker;
    this.liveMatchAlertSender = liveMatchAlertSender;
    this.pollingExecutor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "live-match-poller");
              thread.setDaemon(true);
              return thread;
            });
    this.matchProcessingExecutor =
        Executors.newFixedThreadPool(
            MATCH_PROCESSING_POOL_SIZE,
            runnable -> {
              Thread thread = new Thread(runnable, "live-match-processor");
              thread.setDaemon(true);
              return thread;
            });
  }

  public void start(Duration pollInterval) {
    pollingExecutor.scheduleAtFixedRate(
        this::pollLiveMatches, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void stop() {
    pollingExecutor.shutdownNow();
    matchProcessingExecutor.shutdownNow();
  }

  private void pollLiveMatches() {
    // scheduleAtFixedRate silently stops future runs if a task throws, so every
    // exception must be swallowed here rather than left to propagate.
    try {
      List<Fixture> fixtures = fplApiClient.getFixtures();
      List<Fixture> liveFixtures = fixtures.stream().filter(Fixture::isLive).toList();
      log.info("Polled fixtures: {} currently live", liveFixtures.size());

      liveMatchTracker.retainOnly(
          liveFixtures.stream().map(Fixture::id).collect(Collectors.toSet()));

      if (liveFixtures.isEmpty()) {
        return;
      }

      Map<Integer, String> teamNames =
          fplApiClient.getTeams().stream().collect(Collectors.toMap(Team::id, Team::name));

      for (Fixture fixture : liveFixtures) {
        matchProcessingExecutor.submit(() -> processMatch(fixture, teamNames));
      }
    } catch (Exception e) {
      log.error("Failed to poll live matches", e);
    }
  }

  private void processMatch(Fixture fixture, Map<Integer, String> teamNames) {
    Optional<Fixture> scoreChange = liveMatchTracker.detectScoreChange(fixture);
    scoreChange.ifPresent(changed -> liveMatchAlertSender.sendScoreUpdate(changed, teamNames));
  }
}
