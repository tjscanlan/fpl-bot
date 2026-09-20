package com.fplbot.fplapi;

/**
 * A player, as returned by the FPL API. {@code nowCost} is tenths of a million (e.g. 125 = £12.5m).
 */
public record Player(int id, String webName, int nowCost) {}
