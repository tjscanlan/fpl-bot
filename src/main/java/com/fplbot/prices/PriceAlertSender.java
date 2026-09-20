package com.fplbot.prices;

import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceAlertSender {

  private static final Logger log = LoggerFactory.getLogger(PriceAlertSender.class);

  private final JDA jda;
  private final Long channelId;

  public PriceAlertSender(JDA jda, Long channelId) {
    this.jda = jda;
    this.channelId = channelId;
  }

  public void send(List<PriceChange> changes) {
    if (changes.isEmpty()) {
      return;
    }
    if (channelId == null) {
      log.warn(
          "PRICE_ALERT_CHANNEL_ID is not set; skipping {} price-change alert(s)", changes.size());
      return;
    }

    MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
    if (channel == null) {
      log.error("Price alert channel {} not found or not a message channel", channelId);
      return;
    }

    for (PriceChange change : changes) {
      channel.sendMessage(formatMessage(change)).queue();
    }
    log.info("Sent {} price-change alert(s)", changes.size());
  }

  private static String formatMessage(PriceChange change) {
    boolean risen = change.newCost() > change.oldCost();
    return String.format(
        "%s **%s** has %s: £%.1fm → £%.1fm",
        risen ? "📈" : "📉",
        change.playerName(),
        risen ? "risen" : "fallen",
        change.oldCost() / 10.0,
        change.newCost() / 10.0);
  }
}
