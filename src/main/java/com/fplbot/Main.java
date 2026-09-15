package com.fplbot;

import com.fplbot.commands.PingCommand;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws InterruptedException {
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    String token = dotenv.get("DISCORD_BOT_TOKEN", System.getenv("DISCORD_BOT_TOKEN"));

    if (token == null || token.isBlank()) {
      log.error("DISCORD_BOT_TOKEN is not set; cannot start the bot.");
      System.exit(1);
      return;
    }

    JDA jda =
        JDABuilder.createLight(token).addEventListeners(new PingCommand()).build().awaitReady();

    jda.updateCommands().addCommands(Commands.slash("ping", "Replies with pong.")).queue();

    log.info("Connected to Discord as {}", jda.getSelfUser().getName());
  }
}
