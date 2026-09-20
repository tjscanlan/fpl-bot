package com.fplbot.reminders;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

public class ReminderRepository {

  private final DataSource dataSource;

  public ReminderRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public boolean alreadySent(int gameweekId) throws SQLException {
    String sql = "SELECT 1 FROM sent_reminders WHERE gameweek_id = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, gameweekId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  /** Returns false if another caller already recorded this gameweek (idempotent no-op). */
  public boolean recordSent(int gameweekId) throws SQLException {
    String sql =
        "INSERT INTO sent_reminders (gameweek_id) VALUES (?) ON CONFLICT (gameweek_id) DO NOTHING";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, gameweekId);
      return statement.executeUpdate() > 0;
    }
  }
}
