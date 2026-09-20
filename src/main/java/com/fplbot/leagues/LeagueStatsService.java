package com.fplbot.leagues;

import com.fplbot.fplapi.FplApiClient;
import com.fplbot.fplapi.LeagueStandings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

/**
 * Caches standings per league for a couple of minutes: queries around deadline time come in bursts,
 * and standings don't change quickly enough to justify hitting the FPL API on every one. Uses
 * Caffeine's atomic get-or-compute so a burst of concurrent first-time requests for the same league
 * coalesces into a single upstream call rather than stampeding the API.
 */
public class LeagueStatsService {

  private static final Duration CACHE_TTL = Duration.ofMinutes(2);

  private final FplApiClient fplApiClient;
  private final Cache<Long, LeagueStandings> cache;

  public LeagueStatsService(FplApiClient fplApiClient) {
    this.fplApiClient = fplApiClient;
    this.cache = Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).maximumSize(1000).build();
  }

  public LeagueStandings getStandings(long leagueId) throws Exception {
    try {
      return cache.get(leagueId, this::fetchStandings);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof Exception cause) {
        throw cause;
      }
      throw e;
    }
  }

  private LeagueStandings fetchStandings(long leagueId) {
    try {
      return fplApiClient.getLeagueStandings(leagueId);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
