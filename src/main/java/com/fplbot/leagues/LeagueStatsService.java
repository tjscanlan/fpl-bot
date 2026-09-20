package com.fplbot.leagues;

import com.fplbot.fplapi.FplApiClient;
import com.fplbot.fplapi.LeagueStandings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

/**
 * Caches standings per league for a couple of minutes: queries around deadline time come in bursts,
 * and standings don't change quickly enough to justify hitting the FPL API on every one.
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
    LeagueStandings cached = cache.getIfPresent(leagueId);
    if (cached != null) {
      return cached;
    }
    LeagueStandings standings = fplApiClient.getLeagueStandings(leagueId);
    cache.put(leagueId, standings);
    return standings;
  }
}
