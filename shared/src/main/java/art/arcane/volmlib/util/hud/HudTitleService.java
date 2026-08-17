package art.arcane.volmlib.util.hud;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class HudTitleService {
  public static final String METADATA_KEY = "volmit.hud.title";

  private final Plugin plugin;
  private final HudLocalLedger ledger = new HudLocalLedger();
  private final AtomicLong sessionIds = new AtomicLong();
  private final AtomicLong resolveCounter = new AtomicLong();

  public HudTitleService(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin);
  }

  public HudTitleClaim open(Player player, String purpose, int priority, long ttlMillis) {
    Objects.requireNonNull(player);
    Objects.requireNonNull(purpose);
    if (purpose.indexOf('|') >= 0) {
      throw new IllegalArgumentException("purpose must not contain '|': " + purpose);
    }
    if (ttlMillis <= 0L) {
      throw new IllegalArgumentException("ttlMillis must be positive: " + ttlMillis);
    }
    return new HudTitleClaim(this, player, purpose, priority, ttlMillis, sessionIds.incrementAndGet(), System.currentTimeMillis());
  }

  public void clear(Player player) {
    ledger.clearPrefix(player.getUniqueId() + "|");
    player.removeMetadata(METADATA_KEY, plugin);
  }

  public void shutdown() {
    ledger.clear();
  }

  boolean resolve(Player player, String purpose, int priority, long ttlMillis, long sessionId, long sinceMillis) {
    long now = System.currentTimeMillis();
    if ((resolveCounter.incrementAndGet() & 255L) == 0L) {
      ledger.sweep(now);
    }
    if (!ledger.claim(localKey(player.getUniqueId()), sessionId, priority, sinceMillis, ttlMillis, now)) {
      return false;
    }
    return claimGlobal(player, purpose, priority, ttlMillis, sinceMillis, now);
  }

  void release(Player player, long sessionId) {
    if (ledger.release(localKey(player.getUniqueId()), sessionId)) {
      player.removeMetadata(METADATA_KEY, plugin);
    }
  }

  void retire(UUID playerId, long sessionId) {
    ledger.release(localKey(playerId), sessionId);
  }

  private boolean claimGlobal(Player player, String purpose, int priority, long ttlMillis, long sinceMillis, long nowMillis) {
    HudBid mine = new HudBid(priority, sinceMillis, nowMillis, ttlMillis, purpose);
    player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, mine.encode()));
    List<MetadataValue> values = player.getMetadata(METADATA_KEY);
    List<HudBidder> bidders = new ArrayList<>(values.size());
    for (MetadataValue value : values) {
      if (value.getOwningPlugin() == null) {
        continue;
      }
      bidders.add(new HudBidder(value.getOwningPlugin().getName(), HudBid.decode(value.asString())));
    }
    HudBidder winner = HudBidder.winner(bidders, nowMillis);
    return winner != null
      && winner.ownerName().equals(plugin.getName())
      && winner.bid().sinceMillis() == sinceMillis
      && winner.bid().purpose().equals(purpose);
  }

  private static String localKey(UUID playerId) {
    return playerId + "|TITLE";
  }
}
