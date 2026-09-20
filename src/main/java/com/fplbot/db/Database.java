package com.fplbot.db;

import io.github.cdimascio.dotenv.Dotenv;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

public final class Database {

  private static final String MIGRATIONS_LOCATION = "filesystem:db/migrations";

  private Database() {}

  public static DataSource dataSource(Dotenv dotenv) {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setURL(required(dotenv, "DB_URL"));
    dataSource.setUser(required(dotenv, "DB_USER"));
    dataSource.setPassword(required(dotenv, "DB_PASSWORD"));
    return dataSource;
  }

  public static void migrate(DataSource dataSource) {
    Flyway.configure().dataSource(dataSource).locations(MIGRATIONS_LOCATION).load().migrate();
  }

  private static String required(Dotenv dotenv, String key) {
    String value = dotenv.get(key, System.getenv(key));
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " is not set");
    }
    return value;
  }
}
