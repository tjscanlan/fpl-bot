package com.fplbot.fplapi;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After {@code failureThreshold} consecutive failures, opens and fails fast for {@code
 * openDuration}, then allows one trial request (half-open) to decide whether to close again or
 * reopen.
 */
class CircuitBreaker {

  private enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

  private final int failureThreshold;
  private final Duration openDuration;

  private State state = State.CLOSED;
  private int consecutiveFailures = 0;
  private Instant openedAt;

  CircuitBreaker(int failureThreshold, Duration openDuration) {
    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
  }

  synchronized boolean allowRequest() {
    if (state == State.OPEN) {
      if (Instant.now().isBefore(openedAt.plus(openDuration))) {
        return false;
      }
      state = State.HALF_OPEN;
      log.info("Circuit breaker half-open; allowing a trial request");
    }
    return true;
  }

  synchronized void recordSuccess() {
    if (state != State.CLOSED) {
      log.info("Circuit breaker closed after a successful request");
    }
    state = State.CLOSED;
    consecutiveFailures = 0;
  }

  synchronized void recordFailure() {
    consecutiveFailures++;
    if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
      state = State.OPEN;
      openedAt = Instant.now();
      log.warn("Circuit breaker open after {} consecutive failures", consecutiveFailures);
    }
  }
}
