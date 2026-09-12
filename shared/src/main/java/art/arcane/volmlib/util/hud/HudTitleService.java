package art.arcane.volmlib.util.hud;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class HudTitleService {
  public static final String METADATA_KEY = "volmit.hud.title";
  private static final Consumer<HudTitleClaim> IGNORE_PREEMPTION = claim -> {
  };

  private final Plugin plugin;
  private final HudLocalLedger ledger = new HudLocalLedger();
  private final ConcurrentHashMap<String, HudTitleClaim> localWinners = new ConcurrentHashMap<>();
  private final AtomicLong sessionIds = new AtomicLong();
  private final AtomicLong resolveCounter = new AtomicLong();

  public HudTitleService(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin);
  }

  public HudTitleClaim open(Player player, String purpose, int priority, long ttlMillis) {
    return open(player, purpose, priority, ttlMillis, IGNORE_PREEMPTION);
  }

  public HudTitleClaim open(Player player, String purpose, int priority, long ttlMillis, Consumer<HudTitleClaim> onPreempted) {
    Objects.requireNonNull(player);
    Objects.requireNonNull(purpose);
    Objects.requireNonNull(onPreempted);
    if (purpose.indexOf('|') >= 0) {
      throw new IllegalArgumentException("purpose must not contain '|': " + purpose);
    }
    if (ttlMillis <= 0L) {
      throw new IllegalArgumentException("ttlMillis must be positive: " + ttlMillis);
    }
    return new HudTitleClaim(this, player, purpose, priority, ttlMillis, sessionIds.incrementAndGet(), System.currentTimeMillis(), onPreempted);
  }

  public void clear(Player player) {
    localWinners.remove(localKey(player.getUniqueId()));
    ledger.clearPrefix(player.getUniqueId() + "|");
    player.removeMetadata(METADATA_KEY, plugin);
  }

  public void shutdown() {
    localWinners.clear();
    ledger.clear();
  }

  boolean resolve(HudTitleClaim claim) {
    long now = System.currentTimeMillis();
    if ((resolveCounter.incrementAndGet() & 255L) == 0L) {
      ledger.sweep(now);
    }
    String key = localKey(claim.playerId());
    if (!ledger.claim(key, claim.sessionId(), claim.priority(), claim.sinceMillis(), claim.ttlMillis(), now)) {
      return false;
    }
    HudTitleClaim previous = localWinners.put(key, claim);
    if (previous != null && previous != claim) {
      previous.preempt();
    }
    if (claimGlobal(claim.player(), claim.purpose(), claim.priority(), claim.ttlMillis(), claim.sinceMillis(), now)) {
      return true;
    }
    forget(key, claim.sessionId());
    ledger.release(key, claim.sessionId());
    return false;
  }

  void release(Player player, long sessionId) {
    String key = localKey(player.getUniqueId());
    forget(key, sessionId);
    if (ledger.release(key, sessionId)) {
      player.removeMetadata(METADATA_KEY, plugin);
    }
  }

  boolean dismiss(Player player, long sessionId, String purpose, long sinceMillis) {
    if (!ledger.release(localKey(player.getUniqueId()), sessionId)) {
      return false;
    }
    List<MetadataValue> values = player.getMetadata(METADATA_KEY);
    List<HudBidder> bidders = new ArrayList<>(values.size());
    boolean posted = false;
    for (MetadataValue value : values) {
      Plugin owner = value.getOwningPlugin();
      if (owner == null) {
        continue;
      }
      HudBid bid = HudBid.decode(value.asString());
      if (owner == plugin && bid != null && bid.sinceMillis() == sinceMillis && bid.purpose().equals(purpose)) {
        posted = true;
      }
      bidders.add(new HudBidder(owner.getName(), bid));
    }
    if (!posted) {
      return false;
    }
    HudBidder winner = HudBidder.winner(bidders, System.currentTimeMillis());
    boolean owned = winner != null && winner.ownerName().equals(plugin.getName())
        && winner.bid().sinceMillis() == sinceMillis && winner.bid().purpose().equals(purpose);
    player.removeMetadata(METADATA_KEY, plugin);
    if (owned) {
      player.resetTitle();
    }
    return owned;
  }

  void retire(UUID playerId, long sessionId) {
    String key = localKey(playerId);
    forget(key, sessionId);
    ledger.release(key, sessionId);
  }

  private void forget(String key, long sessionId) {
    localWinners.computeIfPresent(key, (ignored, current) -> current.sessionId() == sessionId ? null : current);
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
