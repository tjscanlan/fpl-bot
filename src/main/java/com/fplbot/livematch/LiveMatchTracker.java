package com.fplbot.livematch;

import com.fplbot.fplapi.Fixture;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each live fixture's last-seen score in memory so repeated polls can detect a change (a
 * goal). State is not persisted: a restart mid-match re-seeds from the next poll rather than
 * reporting a delta for whatever changed while the process was down.
 */
public class LiveMatchTracker {

  private final Map<Integer, Fixture> lastSeen = new ConcurrentHashMap<>();

  /** Empty if this is the first time the fixture has been seen, or its score hasn't changed. */
  public Optional<Fixture> detectScoreChange(Fixture fixture) {
    Fixture previous = lastSeen.put(fixture.id(), fixture);
    if (previous == null) {
      return Optional.empty();
    }
    boolean changed =
        !Objects.equals(previous.teamHScore(), fixture.teamHScore())
            || !Objects.equals(previous.teamAScore(), fixture.teamAScore());
    return changed ? Optional.of(fixture) : Optional.empty();
  }

  /** Drops tracked state for any fixture not in {@code liveFixtureIds} (i.e. no longer live). */
  public void retainOnly(Set<Integer> liveFixtureIds) {
    lastSeen.keySet().retainAll(liveFixtureIds);
  }
}
