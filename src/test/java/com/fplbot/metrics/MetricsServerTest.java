package com.fplbot.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class MetricsServerTest {

  private static final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void allowsUnauthenticatedRequestsWhenNoTokenIsConfigured() throws Exception {
    MetricsServer server =
        new MetricsServer(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), 0, null);
    server.start();

    assertEquals(200, get(server.getPort(), null));
  }

  @Test
  void rejectsRequestsMissingTheBearerToken() throws Exception {
    MetricsServer server =
        new MetricsServer(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), 0, "secret");
    server.start();

    assertEquals(401, get(server.getPort(), null));
  }

  @Test
  void rejectsRequestsWithTheWrongBearerToken() throws Exception {
    MetricsServer server =
        new MetricsServer(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), 0, "secret");
    server.start();

    assertEquals(401, get(server.getPort(), "Bearer wrong"));
  }

  @Test
  void allowsRequestsWithTheCorrectBearerToken() throws Exception {
    MetricsServer server =
        new MetricsServer(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), 0, "secret");
    server.start();

    assertEquals(200, get(server.getPort(), "Bearer secret"));
  }

  private static int get(int port, String authorizationHeader) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/metrics")).GET();
    if (authorizationHeader != null) {
      request.header("Authorization", authorizationHeader);
    }
    return httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
  }
}
