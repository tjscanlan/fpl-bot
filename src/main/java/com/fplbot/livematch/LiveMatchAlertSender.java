package com.fplbot.livematch;

import com.fplbot.fplapi.Fixture;
import java.util.Map;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveMatchAlertSender {

  private static final Logger log = LoggerFactory.getLogger(LiveMatchAlertSender.class);

  private final JDA jda;
  private final Long channelId;

  public LiveMatchAlertSender(JDA jda, Long channelId) {
    this.jda = jda;
    this.channelId = channelId;
  }

  public void sendScoreUpdate(Fixture fixture, Map<Integer, String> teamNames) {
    if (channelId == null) {
      log.warn(
          "LIVE_MATCH_CHANNEL_ID is not set; skipping score update for fixture {}", fixture.id());
      return;
    }

    MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
    if (channel == null) {
      log.error("Live match channel {} not found or not a message channel", channelId);
      return;
    }

    String teamHName = teamNames.getOrDefault(fixture.teamH(), "Team " + fixture.teamH());
    String teamAName = teamNames.getOrDefault(fixture.teamA(), "Team " + fixture.teamA());
    channel
        .sendMessage(
            String.format(
                "⚽ %d' — %s %d-%d %s",
                fixture.minutes(),
                teamHName,
                fixture.teamHScore(),
                fixture.teamAScore(),
                teamAName))
        .queue();
  }
}
