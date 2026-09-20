package com.fplbot.leagues;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;

public class LeagueRepository {

  public record LeagueConfig(long id, long fplLeagueId) {}

  private final DataSource dataSource;

  public LeagueRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public Optional<LeagueConfig> findLeagueForGuild(long discordGuildId) throws SQLException {
    String sql = "SELECT id, fpl_league_id FROM leagues WHERE discord_guild_id = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, discordGuildId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(
            new LeagueConfig(resultSet.getLong("id"), resultSet.getLong("fpl_league_id")));
      }
    }
  }

  /** FPL entry IDs of registered Discord users who are members of this (internal) league. */
  public Set<Long> findMemberFplEntryIds(long leagueId) throws SQLException {
    String sql =
        "SELECT u.fpl_entry_id FROM league_members lm "
            + "JOIN users u ON u.id = lm.user_id "
            + "WHERE lm.league_id = ? AND u.fpl_entry_id IS NOT NULL";
    Set<Long> entryIds = new HashSet<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, leagueId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          entryIds.add(resultSet.getLong("fpl_entry_id"));
        }
      }
    }
    return entryIds;
  }
}
