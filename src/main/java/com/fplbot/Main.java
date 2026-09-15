package com.fplbot;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
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

    JDA jda = JDABuilder.createLight(token).build().awaitReady();

    log.info("Connected to Discord as {}", jda.getSelfUser().getName());
  }
}
