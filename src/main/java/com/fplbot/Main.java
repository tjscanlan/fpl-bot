package com.fplbot;

import com.fplbot.commands.LeagueStatsCommand;
import com.fplbot.commands.PingCommand;
import com.fplbot.db.Database;
import com.fplbot.fplapi.FplApiClient;
import com.fplbot.leagues.LeagueRepository;
import com.fplbot.leagues.LeagueStatsService;
import com.fplbot.metrics.MetricsServer;
import com.fplbot.prices.PriceAlertSender;
import com.fplbot.prices.PriceChangeService;
import com.fplbot.prices.PriceRepository;
import com.fplbot.reminders.ReminderRepository;
import com.fplbot.reminders.ReminderService;
import com.fplbot.scheduler.LiveMatchScheduler;
import com.fplbot.scheduler.PriceCheckScheduler;
import com.fplbot.scheduler.ReminderScheduler;
import io.github.cdimascio.dotenv.Dotenv;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import javax.sql.DataSource;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws InterruptedException, IOException {
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    String token = dotenv.get("DISCORD_BOT_TOKEN", System.getenv("DISCORD_BOT_TOKEN"));

    if (token == null || token.isBlank()) {
      log.error("DISCORD_BOT_TOKEN is not set; cannot start the bot.");
      System.exit(1);
      return;
    }

    String metricsPortEnv = dotenv.get("METRICS_PORT", System.getenv("METRICS_PORT"));
    int metricsPort = metricsPortEnv != null ? Integer.parseInt(metricsPortEnv) : 8081;

    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    new MetricsServer(registry, metricsPort).start();
    log.info("Metrics available at http://localhost:{}/metrics", metricsPort);

    DataSource dataSource = Database.dataSource(dotenv);
    Database.migrate(dataSource);
    log.info("Database migrations applied");

    FplApiClient fplApiClient = new FplApiClient();
    LeagueRepository leagueRepository = new LeagueRepository(dataSource);
    LeagueStatsService leagueStatsService = new LeagueStatsService(fplApiClient);

    JDA jda =
        JDABuilder.createLight(token)
            .addEventListeners(
                new PingCommand(registry),
                new LeagueStatsCommand(leagueRepository, leagueStatsService, registry))
            .build()
            .awaitReady();

    jda.updateCommands()
        .addCommands(
            Commands.slash("ping", "Replies with pong."),
            Commands.slash(
                "league-stats", "Show the standings for this server's tracked FPL league."))
        .queue();

    log.info("Connected to Discord as {}", jda.getSelfUser().getName());

    String reminderChannelIdEnv =
        dotenv.get("REMINDER_CHANNEL_ID", System.getenv("REMINDER_CHANNEL_ID"));
    if (reminderChannelIdEnv == null || reminderChannelIdEnv.isBlank()) {
      log.warn("REMINDER_CHANNEL_ID is not set; deadline reminders are disabled.");
    } else {
      long reminderChannelId = Long.parseLong(reminderChannelIdEnv);

      String leadMinutesEnv =
          dotenv.get("REMINDER_LEAD_MINUTES", System.getenv("REMINDER_LEAD_MINUTES"));
      long leadMinutes = leadMinutesEnv != null ? Long.parseLong(leadMinutesEnv) : 60;

      String pollIntervalEnv =
          dotenv.get(
              "REMINDER_POLL_INTERVAL_MINUTES", System.getenv("REMINDER_POLL_INTERVAL_MINUTES"));
      long pollIntervalMinutes = pollIntervalEnv != null ? Long.parseLong(pollIntervalEnv) : 15;

      ReminderService reminderService =
          new ReminderService(
              fplApiClient,
              new ReminderRepository(dataSource),
              jda,
              reminderChannelId,
              Duration.ofMinutes(leadMinutes),
              registry);

      new ReminderScheduler(reminderService).start(Duration.ofMinutes(pollIntervalMinutes));
      log.info("Reminder scheduler started, polling every {} minutes", pollIntervalMinutes);
    }

    String priceAlertChannelIdEnv =
        dotenv.get("PRICE_ALERT_CHANNEL_ID", System.getenv("PRICE_ALERT_CHANNEL_ID"));
    Long priceAlertChannelId =
        priceAlertChannelIdEnv != null && !priceAlertChannelIdEnv.isBlank()
            ? Long.parseLong(priceAlertChannelIdEnv)
            : null;
    if (priceAlertChannelId == null) {
      log.warn("PRICE_ALERT_CHANNEL_ID is not set; price-change alerts are disabled.");
    }

    String priceCheckTimeEnv = dotenv.get("PRICE_CHECK_TIME", System.getenv("PRICE_CHECK_TIME"));
    LocalTime priceCheckTime =
        priceCheckTimeEnv != null ? LocalTime.parse(priceCheckTimeEnv) : LocalTime.of(1, 30);
    PriceChangeService priceChangeService =
        new PriceChangeService(fplApiClient, new PriceRepository(dataSource), registry);
    PriceAlertSender priceAlertSender = new PriceAlertSender(jda, priceAlertChannelId);
    new PriceCheckScheduler(priceChangeService, priceAlertSender, priceCheckTime).start();

    String liveMatchPollIntervalEnv =
        dotenv.get(
            "LIVE_MATCH_POLL_INTERVAL_SECONDS", System.getenv("LIVE_MATCH_POLL_INTERVAL_SECONDS"));
    long liveMatchPollIntervalSeconds =
        liveMatchPollIntervalEnv != null ? Long.parseLong(liveMatchPollIntervalEnv) : 30;
    new LiveMatchScheduler(fplApiClient).start(Duration.ofSeconds(liveMatchPollIntervalSeconds));
  }
}
