package com.fplbot;

import com.fplbot.commands.PingCommand;
import com.fplbot.db.Database;
import com.fplbot.fplapi.FplApiClient;
import com.fplbot.metrics.MetricsServer;
import com.fplbot.reminders.ReminderRepository;
import com.fplbot.reminders.ReminderService;
import com.fplbot.scheduler.ReminderScheduler;
import io.github.cdimascio.dotenv.Dotenv;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.time.Duration;
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

    JDA jda =
        JDABuilder.createLight(token)
            .addEventListeners(new PingCommand(registry))
            .build()
            .awaitReady();

    jda.updateCommands().addCommands(Commands.slash("ping", "Replies with pong.")).queue();

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
              new FplApiClient(),
              new ReminderRepository(dataSource),
              jda,
              reminderChannelId,
              Duration.ofMinutes(leadMinutes),
              registry);

      new ReminderScheduler(reminderService).start(Duration.ofMinutes(pollIntervalMinutes));
      log.info("Reminder scheduler started, polling every {} minutes", pollIntervalMinutes);
    }
  }
}
