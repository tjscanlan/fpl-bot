package com.fplbot.fplapi;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class FplApiClientTest {

  private static final String EMPTY_BOOTSTRAP_JSON = "{\"events\":[],\"elements\":[]}";

  @SuppressWarnings("unchecked")
  @Test
  void retriesOnTransientFailureThenSucceeds() throws Exception {
    HttpClient httpClient = mock(HttpClient.class);
    HttpResponse<String> successResponse = mock(HttpResponse.class);
    when(successResponse.statusCode()).thenReturn(200);
    when(successResponse.body()).thenReturn(EMPTY_BOOTSTRAP_JSON);

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection reset"))
        .thenReturn(successResponse);

    FplApiClient client = new FplApiClient(httpClient);
    List<Gameweek> gameweeks = client.getGameweeks();

    assertTrue(gameweeks.isEmpty());
    verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  void throwsAfterExhaustingAllRetryAttempts() throws Exception {
    HttpClient httpClient = mock(HttpClient.class);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection reset"));

    FplApiClient client = new FplApiClient(httpClient);

    assertThrows(IOException.class, client::getGameweeks);
    // 3 attempts: matches FplApiClient's private MAX_ATTEMPTS.
    verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  void circuitBreakerOpensAndFailsFastAfterRepeatedFailures() throws Exception {
    HttpClient httpClient = mock(HttpClient.class);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection reset"));

    FplApiClient client = new FplApiClient(httpClient);

    // 3 consecutive exhausted calls (3 attempts each) match FplApiClient's private
    // CIRCUIT_BREAKER_FAILURE_THRESHOLD and should open the circuit breaker.
    for (int i = 0; i < 3; i++) {
      assertThrows(IOException.class, client::getGameweeks);
    }
    verify(httpClient, times(9)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

    // The next call should fail fast, without hitting the network at all.
    IOException exception = assertThrows(IOException.class, client::getGameweeks);
    assertTrue(exception.getMessage().contains("circuit breaker"));
    verify(httpClient, times(9)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }
}
