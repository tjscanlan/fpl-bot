package com.fplbot.leagues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fplbot.fplapi.FplApiClient;
import com.fplbot.fplapi.LeagueStandings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Simulates the burst of concurrent /league-stats queries that happens right around a gameweek
 * deadline, when many users check standings for the same league at once.
 */
class LeagueStatsServiceLoadTest {

  private static final int CONCURRENT_QUERIES = 50;
  private static final long LEAGUE_ID = 12345L;

  @Test
  void concurrentQueriesForTheSameLeagueHitTheApiOnlyOnce() throws Exception {
    FplApiClient fplApiClient = mock(FplApiClient.class);
    LeagueStandings standings =
        new LeagueStandings(
            new LeagueStandings.LeagueInfo(LEAGUE_ID, "Test League"),
            new LeagueStandings.Standings(List.of()));

    when(fplApiClient.getLeagueStandings(LEAGUE_ID))
        .thenAnswer(
            invocation -> {
              // Simulate real network latency so the concurrent callers actually overlap.
              Thread.sleep(200);
              return standings;
            });

    LeagueStatsService service = new LeagueStatsService(fplApiClient);
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_QUERIES);
    List<Future<LeagueStandings>> futures;
    try {
      Callable<LeagueStandings> query = () -> service.getStandings(LEAGUE_ID);
      futures = executor.invokeAll(Collections.nCopies(CONCURRENT_QUERIES, query));
    } finally {
      executor.shutdown();
    }

    for (Future<LeagueStandings> future : futures) {
      assertEquals(standings, future.get());
    }
    verify(fplApiClient, times(1)).getLeagueStandings(LEAGUE_ID);
  }
}
