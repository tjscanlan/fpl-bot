package com.fplbot.prices;

public record PriceChange(int playerId, String playerName, int oldCost, int newCost) {}
