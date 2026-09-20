package com.fplbot.fplapi;

import java.time.Instant;

public record Gameweek(
    int id, String name, Instant deadlineTime, boolean isNext, boolean isCurrent) {}
