package com.fplbot.commands;

import com.fplbot.fplapi.LeagueStandings;
import com.fplbot.leagues.LeagueRepository;
import com.fplbot.leagues.LeagueStatsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeagueStatsCommand extends ListenerAdapter {

  private static final Logger log = LoggerFactory.getLogger(LeagueStatsCommand.class);
  private static final int MAX_ROWS_SHOWN = 10;

  private final LeagueRepository leagueRepository;
  private final LeagueStatsService leagueStatsService;
  private final Timer queryLatencyTimer;

  public LeagueStatsCommand(
      LeagueRepository leagueRepository,
      LeagueStatsService leagueStatsService,
      MeterRegistry registry) {
    this.leagueRepository = leagueRepository;
    this.leagueStatsService = leagueStatsService;
    this.queryLatencyTimer =
        Timer.builder("bot.league_stats.query.latency")
            .description("Time from receiving /league-stats to the reply being sent")
            .register(registry);
  }

  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    if (!event.getName().equals("league-stats")) {
      return;
    }
    if (event.getGuild() == null) {
      event.reply("This command only works in a server.").setEphemeral(true).queue();
      return;
    }

    Timer.Sample sample = Timer.start();
    event.deferReply().queue();
    long guildId = event.getGuild().getIdLong();

    // The FPL fetch (plus retries/backoff) is blocking, so it runs off JDA's event
    // thread rather than tying it up.
    CompletableFuture.supplyAsync(() -> fetchStandingsMessage(guildId))
        .thenAccept(
            message ->
                event.getHook().sendMessage(message).queue(v -> sample.stop(queryLatencyTimer)));
  }

  private String fetchStandingsMessage(long guildId) {
    try {
      Optional<LeagueRepository.LeagueConfig> leagueConfig =
          leagueRepository.findLeagueForGuild(guildId);
      if (leagueConfig.isEmpty()) {
        return "No FPL league is configured for this server yet.";
      }

      LeagueStandings standings = leagueStatsService.getStandings(leagueConfig.get().fplLeagueId());
      Set<Long> knownEntryIds = leagueRepository.findMemberFplEntryIds(leagueConfig.get().id());
      return formatStandings(standings, knownEntryIds);
    } catch (Exception e) {
      log.error("Failed to fetch league stats", e);
      return "Sorry, couldn't fetch league stats right now.";
    }
  }

  private static String formatStandings(LeagueStandings standings, Set<Long> knownEntryIds) {
    StringBuilder message = new StringBuilder();
    message.append("**").append(standings.league().name()).append("**\n");

    List<LeagueStandings.StandingEntry> results = standings.standings().results();
    int rowsShown = Math.min(results.size(), MAX_ROWS_SHOWN);
    for (int i = 0; i < rowsShown; i++) {
      LeagueStandings.StandingEntry entry = results.get(i);
      String marker = knownEntryIds.contains(entry.entry()) ? "⭐ " : "";
      message
          .append(marker)
          .append(entry.rank())
          .append(". ")
          .append(entry.entryName())
          .append(" (")
          .append(entry.playerName())
          .append(") — ")
          .append(entry.total())
          .append(" pts\n");
    }
    return message.toString();
  }
}
