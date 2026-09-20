package com.fplbot.fplapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record BootstrapStatic(List<Gameweek> events, List<Player> elements, List<Team> teams) {}
