package com.fplbot.fplapi;

public record Fixture(
    int id,
    Integer event,
    int teamH,
    int teamA,
    Integer teamHScore,
    Integer teamAScore,
    boolean started,
    boolean finished,
    boolean finishedProvisional,
    int minutes) {

  public boolean isLive() {
    return started && !finished;
  }
}
