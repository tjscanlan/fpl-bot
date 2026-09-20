package com.fplbot.prices;

import com.fplbot.fplapi.FplApiClient;
import com.fplbot.fplapi.Player;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceChangeService {

  private static final Logger log = LoggerFactory.getLogger(PriceChangeService.class);

  private final FplApiClient fplApiClient;
  private final PriceRepository priceRepository;
  private final Timer detectionLatencyTimer;

  public PriceChangeService(
      FplApiClient fplApiClient, PriceRepository priceRepository, MeterRegistry registry) {
    this.fplApiClient = fplApiClient;
    this.priceRepository = priceRepository;
    this.detectionLatencyTimer =
        Timer.builder("bot.price.detection.latency")
            .description(
                "Time to fetch current FPL prices and diff them against the last known values")
            .register(registry);
  }

  /**
   * A player with no stored price is seeded as a new baseline rather than reported as a change, so
   * the first run (or a newly added player) doesn't produce spurious alerts.
   */
  public List<PriceChange> checkForPriceChanges() throws Exception {
    Timer.Sample sample = Timer.start();
    try {
      List<Player> players = fplApiClient.getPlayers();
      Map<Integer, Integer> knownPrices = priceRepository.getAllPrices();

      List<PriceChange> changes = new ArrayList<>();
      for (Player player : players) {
        Integer knownCost = knownPrices.get(player.id());
        boolean isNewPlayer = knownCost == null;
        boolean priceChanged = !isNewPlayer && !knownCost.equals(player.nowCost());

        if (priceChanged) {
          changes.add(new PriceChange(player.id(), player.webName(), knownCost, player.nowCost()));
          priceRepository.recordPriceChange(player.id(), knownCost, player.nowCost());
        }
        if (isNewPlayer || priceChanged) {
          priceRepository.upsertPrice(player.id(), player.nowCost());
        }
      }

      log.info("Checked {} players, detected {} price changes", players.size(), changes.size());
      return changes;
    } finally {
      sample.stop(detectionLatencyTimer);
    }
  }
}
