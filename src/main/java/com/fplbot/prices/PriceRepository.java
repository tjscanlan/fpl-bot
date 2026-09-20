package com.fplbot.prices;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

public class PriceRepository {

  private final DataSource dataSource;

  public PriceRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public Map<Integer, Integer> getAllPrices() throws SQLException {
    String sql = "SELECT player_id, now_cost FROM player_prices";
    Map<Integer, Integer> prices = new HashMap<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        prices.put(resultSet.getInt("player_id"), resultSet.getInt("now_cost"));
      }
    }
    return prices;
  }

  public void upsertPrice(int playerId, int nowCost) throws SQLException {
    String sql =
        "INSERT INTO player_prices (player_id, now_cost, updated_at) VALUES (?, ?, now()) "
            + "ON CONFLICT (player_id) DO UPDATE SET now_cost = EXCLUDED.now_cost, updated_at ="
            + " now()";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, playerId);
      statement.setInt(2, nowCost);
      statement.executeUpdate();
    }
  }

  public void recordPriceChange(int playerId, int oldCost, int newCost) throws SQLException {
    String sql = "INSERT INTO price_changes (player_id, old_cost, new_cost) VALUES (?, ?, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, playerId);
      statement.setInt(2, oldCost);
      statement.setInt(3, newCost);
      statement.executeUpdate();
    }
  }
}
