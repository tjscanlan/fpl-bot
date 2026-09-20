package com.fplbot.fplapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LeagueStandings(LeagueInfo league, Standings standings) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LeagueInfo(long id, String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Standings(List<StandingEntry> results) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StandingEntry(
      long entry, String entryName, String playerName, int rank, int lastRank, int total) {}
}
