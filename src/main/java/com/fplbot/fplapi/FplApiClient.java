package com.fplbot.fplapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for the unofficial, undocumented Fantasy Premier League API. It has no SLA, so every
 * request is retried with backoff rather than failed on the first hiccup.
 */
public class FplApiClient {

  private static final Logger log = LoggerFactory.getLogger(FplApiClient.class);
  private static final URI BOOTSTRAP_STATIC_URI =
      URI.create("https://fantasy.premierleague.com/api/bootstrap-static/");
  private static final URI FIXTURES_URI =
      URI.create("https://fantasy.premierleague.com/api/fixtures/");
  private static final int MAX_ATTEMPTS = 3;
  private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
  private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3;
  private static final Duration CIRCUIT_BREAKER_OPEN_DURATION = Duration.ofMinutes(5);

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final CircuitBreaker circuitBreaker;

  public FplApiClient() {
    this(HttpClient.newHttpClient());
  }

  public FplApiClient(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    this.circuitBreaker =
        new CircuitBreaker(CIRCUIT_BREAKER_FAILURE_THRESHOLD, CIRCUIT_BREAKER_OPEN_DURATION);
  }

  public List<Gameweek> getGameweeks() throws IOException, InterruptedException {
    String body = getWithRetry(BOOTSTRAP_STATIC_URI);
    return objectMapper.readValue(body, BootstrapStatic.class).events();
  }

  public Optional<Gameweek> getNextDeadline() throws IOException, InterruptedException {
    return getGameweeks().stream().filter(Gameweek::isNext).findFirst();
  }

  public List<Player> getPlayers() throws IOException, InterruptedException {
    String body = getWithRetry(BOOTSTRAP_STATIC_URI);
    return objectMapper.readValue(body, BootstrapStatic.class).elements();
  }

  public LeagueStandings getLeagueStandings(long leagueId)
      throws IOException, InterruptedException {
    URI uri =
        URI.create(
            "https://fantasy.premierleague.com/api/leagues-classic/" + leagueId + "/standings/");
    String body = getWithRetry(uri);
    return objectMapper.readValue(body, LeagueStandings.class);
  }

  public List<Fixture> getFixtures() throws IOException, InterruptedException {
    String body = getWithRetry(FIXTURES_URI);
    return objectMapper.readValue(body, new TypeReference<List<Fixture>>() {});
  }

  public List<Team> getTeams() throws IOException, InterruptedException {
    String body = getWithRetry(BOOTSTRAP_STATIC_URI);
    return objectMapper.readValue(body, BootstrapStatic.class).teams();
  }

  private String getWithRetry(URI uri) throws IOException, InterruptedException {
    if (!circuitBreaker.allowRequest()) {
      throw new IOException("FPL API circuit breaker is open; failing fast");
    }

    HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(10)).build();

    IOException lastFailure = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 == 2) {
          circuitBreaker.recordSuccess();
          return response.body();
        }
        lastFailure = new IOException("FPL API returned HTTP " + response.statusCode());
      } catch (IOException e) {
        lastFailure = e;
      }

      if (attempt < MAX_ATTEMPTS) {
        Duration backoff = INITIAL_BACKOFF.multipliedBy(1L << (attempt - 1));
        log.warn(
            "FPL API request failed (attempt {}/{}), retrying in {}",
            attempt,
            MAX_ATTEMPTS,
            backoff,
            lastFailure);
        Thread.sleep(backoff.toMillis());
      }
    }
    circuitBreaker.recordFailure();
    throw lastFailure;
  }
}
