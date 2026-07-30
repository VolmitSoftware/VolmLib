package art.arcane.volmlib.util.hud;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class HudSlotService {
  private final Plugin plugin;
  private final HudLocalLedger ledger = new HudLocalLedger();
  private final AtomicLong sessionIds = new AtomicLong();
  private final AtomicLong resolveCounter = new AtomicLong();

  public HudSlotService(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin);
  }

  public HudSlotClaim open(Player player, HudSlotRequest request) {
    Objects.requireNonNull(player);
    Objects.requireNonNull(request);
    return new HudSlotClaim(this, player, request, sessionIds.incrementAndGet(), System.currentTimeMillis());
  }

  public void clear(Player player) {
    ledger.clearPrefix(player.getUniqueId() + "|");
    for (HudSurface surface : HudSurface.values()) {
      if (surface.isArbitrated()) {
        player.removeMetadata(surface.metadataKey(), plugin);
      }
    }
  }

  public void shutdown() {
    ledger.clear();
  }

  HudSurface resolve(Player player, HudSlotRequest request, long sessionId, long sinceMillis) {
    long now = System.currentTimeMillis();
    if ((resolveCounter.incrementAndGet() & 255L) == 0L) {
      ledger.sweep(now);
    }
    List<HudSurface> preferences = request.preferences();
    HudSurface granted = null;
    for (HudSurface surface : preferences) {
      if (!surface.isArbitrated()) {
        granted = surface;
        break;
      }
      if (!ledger.claim(localKey(player, surface), sessionId, request.priority(), sinceMillis, request.ttlMillis(), now)) {
        continue;
      }
      if (claimGlobal(player, surface, request, sinceMillis, now)) {
        granted = surface;
        break;
      }
    }
    if (granted != null) {
      releaseBeyond(player, request, granted, sessionId);
    }
    return granted;
  }

  void releaseAll(Player player, HudSlotRequest request, long sessionId) {
    for (HudSurface surface : request.preferences()) {
      if (surface.isArbitrated()) {
        releaseSurface(player, surface, sessionId);
      }
    }
  }

  private boolean claimGlobal(Player player, HudSurface surface, HudSlotRequest request, long sinceMillis, long nowMillis) {
    HudBid mine = new HudBid(request.priority(), sinceMillis, nowMillis, request.ttlMillis(), request.purpose());
    player.setMetadata(surface.metadataKey(), new FixedMetadataValue(plugin, mine.encode()));
    List<MetadataValue> values = player.getMetadata(surface.metadataKey());
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
      && winner.bid().purpose().equals(request.purpose());
  }

  private void releaseBeyond(Player player, HudSlotRequest request, HudSurface granted, long sessionId) {
    List<HudSurface> preferences = request.preferences();
    int grantedIndex = preferences.indexOf(granted);
    for (int i = grantedIndex + 1; i < preferences.size(); i++) {
      HudSurface surface = preferences.get(i);
      if (surface.isArbitrated()) {
        releaseSurface(player, surface, sessionId);
      }
    }
  }

  private void releaseSurface(Player player, HudSurface surface, long sessionId) {
    if (ledger.release(localKey(player, surface), sessionId)) {
      player.removeMetadata(surface.metadataKey(), plugin);
    }
  }

  private static String localKey(Player player, HudSurface surface) {
    return player.getUniqueId() + "|" + surface.name();
  }
}
