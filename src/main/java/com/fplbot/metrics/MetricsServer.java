package com.fplbot.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class MetricsServer {

  private final HttpServer server;

  /**
   * {@code authToken}, if non-null, requires a matching {@code Authorization: Bearer <token>}
   * header on every request; if null, the endpoint is open (e.g. for local dev).
   */
  public MetricsServer(PrometheusMeterRegistry registry, int port, String authToken)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext(
        "/metrics",
        exchange -> {
          if (authToken != null && !isAuthorized(exchange, authToken)) {
            byte[] body = "Unauthorized".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(body);
            }
            return;
          }

          byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
  }

  public void start() {
    server.start();
  }

  public int getPort() {
    return server.getAddress().getPort();
  }

  private static boolean isAuthorized(HttpExchange exchange, String expectedToken) {
    String header = exchange.getRequestHeaders().getFirst("Authorization");
    if (header == null) {
      return false;
    }
    String expected = "Bearer " + expectedToken;
    return MessageDigest.isEqual(
        header.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }
}
