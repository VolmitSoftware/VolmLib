package art.arcane.volmlib.util.hud;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public final class HudBossBarLane {
  public static final String METADATA_KEY = "volmit.hud.bossbars";
  private static final String INDEX_KEY = METADATA_KEY + "|index";
  private static final String INDEX_SEPARATOR = ",";

  private final Plugin plugin;
  private final LongSupplier clock;
  private final BossBarFactory bossBars;
  private final SweepScheduler sweeper;
  private final AtomicBoolean sweeping = new AtomicBoolean();
  private final ConcurrentHashMap<String, TrackedBar> bars = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Set<String>> asserted = new ConcurrentHashMap<>();

  public HudBossBarLane() {
    this(null, System::currentTimeMillis, Bukkit::createBossBar, (runnable, delayTicks) -> false);
  }

  public HudBossBarLane(Plugin plugin) {
    this(plugin, System::currentTimeMillis, Bukkit::createBossBar,
        (runnable, delayTicks) -> FoliaScheduler.runGlobal(plugin, runnable, delayTicks));
  }

  HudBossBarLane(Plugin plugin, LongSupplier clock, BossBarFactory bossBars, SweepScheduler sweeper) {
    this.plugin = plugin;
    this.clock = clock;
    this.bossBars = bossBars;
    this.sweeper = sweeper;
  }

  public void show(Player player, String laneId, String title, double progress, BarColor color, BarStyle style, long staleMillis) {
    show(player, laneId, HudPriority.STATUS, title, progress, color, style, staleMillis, Integer.MAX_VALUE);
  }

  public boolean show(Player player, String laneId, int priority, String title, double progress, BarColor color, BarStyle style, long staleMillis, int maxBars) {
    long now = clock.getAsLong();
    TrackedBar tracked = bars.computeIfAbsent(laneKey(player.getUniqueId(), laneId), key -> new TrackedBar(bossBars.create(title, color, style), player, style, now));
    tracked.player = player;
    tracked.updatedMillis = now;
    tracked.staleMillis = staleMillis;
    boolean granted = bid(player, laneId, priority, staleMillis, now, maxBars, tracked.sinceMillis);
    if (granted) {
      tracked.bar.setTitle(title);
      tracked.bar.setProgress(Math.max(0.0D, Math.min(1.0D, progress)));
      tracked.bar.setColor(color);
      if (tracked.style != style) {
        tracked.bar.setStyle(style);
        tracked.style = style;
      }
      tracked.bar.addPlayer(player);
      tracked.granted = true;
    } else if (tracked.granted) {
      tracked.bar.removeAll();
      tracked.granted = false;
    }
    sweep(now);
    return granted;
  }

  public void hide(Player player, String laneId) {
    TrackedBar tracked = bars.remove(laneKey(player.getUniqueId(), laneId));
    if (tracked != null) {
      tracked.bar.removeAll();
    }
    withdraw(player, laneId);
  }

  public void hideAll(Player player) {
    String prefix = player.getUniqueId() + "|";
    Iterator<Map.Entry<String, TrackedBar>> iterator = bars.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, TrackedBar> entry = iterator.next();
      if (entry.getKey().startsWith(prefix)) {
        entry.getValue().bar.removeAll();
        iterator.remove();
      }
    }
    withdrawAll(player);
  }

  public void retire(UUID playerId, String laneId) {
    bars.remove(laneKey(playerId, laneId));
    Set<String> lanes = asserted.get(playerId);
    if (lanes != null) {
      lanes.remove(laneId);
      if (lanes.isEmpty()) {
        asserted.remove(playerId);
      }
    }
  }

  public void sweep() {
    sweep(clock.getAsLong());
  }

  public void startSweeper(long periodTicks) {
    if (plugin == null || !sweeping.compareAndSet(false, true)) {
      return;
    }
    scheduleSweep(Math.max(1L, periodTicks));
  }

  public void shutdown() {
    sweeping.set(false);
    Iterator<Map.Entry<String, TrackedBar>> iterator = bars.entrySet().iterator();
    while (iterator.hasNext()) {
      iterator.next().getValue().bar.removeAll();
      iterator.remove();
    }
    asserted.clear();
  }

  private void scheduleSweep(long periodTicks) {
    sweeper.schedule(() -> {
      if (!sweeping.get()) {
        return;
      }
      sweep();
      scheduleSweep(periodTicks);
    }, periodTicks);
  }

  private void sweep(long nowMillis) {
    Iterator<Map.Entry<String, TrackedBar>> iterator = bars.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, TrackedBar> entry = iterator.next();
      TrackedBar tracked = entry.getValue();
      if (nowMillis - tracked.updatedMillis <= tracked.staleMillis) {
        continue;
      }
      tracked.bar.removeAll();
      iterator.remove();
      withdraw(tracked.player, laneIdOf(entry.getKey()));
      tracked.player = null;
    }
  }

  private boolean bid(Player player, String laneId, int priority, long staleMillis, long nowMillis, int maxBars, long sinceMillis) {
    if (plugin == null) {
      return true;
    }
    player.setMetadata(METADATA_KEY + "|" + laneId, new FixedMetadataValue(plugin, new HudBid(priority, sinceMillis, nowMillis, staleMillis, laneId).encode()));
    asserted.computeIfAbsent(player.getUniqueId(), key -> ConcurrentHashMap.newKeySet()).add(laneId);
    publishIndex(player);
    return rankOf(collect(player), laneId, nowMillis) < maxBars;
  }

  private void withdraw(Player player, String laneId) {
    if (plugin == null || player == null || laneId == null) {
      return;
    }
    player.removeMetadata(METADATA_KEY + "|" + laneId, plugin);
    Set<String> lanes = asserted.get(player.getUniqueId());
    if (lanes != null) {
      lanes.remove(laneId);
    }
    publishIndex(player);
  }

  private void withdrawAll(Player player) {
    if (plugin == null) {
      return;
    }
    Set<String> lanes = asserted.remove(player.getUniqueId());
    if (lanes == null) {
      return;
    }
    for (String laneId : lanes) {
      player.removeMetadata(METADATA_KEY + "|" + laneId, plugin);
    }
    player.removeMetadata(INDEX_KEY, plugin);
  }

  private void publishIndex(Player player) {
    Set<String> lanes = asserted.get(player.getUniqueId());
    if (lanes == null || lanes.isEmpty()) {
      asserted.remove(player.getUniqueId());
      player.removeMetadata(INDEX_KEY, plugin);
      return;
    }
    player.setMetadata(INDEX_KEY, new FixedMetadataValue(plugin, String.join(INDEX_SEPARATOR, lanes)));
  }

  private List<HudBidder> collect(Player player) {
    List<HudBidder> bidders = new ArrayList<>();
    for (MetadataValue index : player.getMetadata(INDEX_KEY)) {
      Plugin owner = index.getOwningPlugin();
      if (owner == null) {
        continue;
      }
      for (String laneId : index.asString().split(INDEX_SEPARATOR)) {
        if (laneId.isEmpty()) {
          continue;
        }
        HudBid bid = bidOf(player, owner, laneId);
        if (bid != null) {
          bidders.add(new HudBidder(owner.getName(), bid));
        }
      }
    }
    return bidders;
  }

  private HudBid bidOf(Player player, Plugin owner, String laneId) {
    for (MetadataValue value : player.getMetadata(METADATA_KEY + "|" + laneId)) {
      if (owner.equals(value.getOwningPlugin())) {
        return HudBid.decode(value.asString());
      }
    }
    return null;
  }

  private int rankOf(List<HudBidder> bidders, String laneId, long nowMillis) {
    List<HudBidder> remaining = new ArrayList<>(bidders);
    String owner = plugin.getName();
    for (int rank = 0; !remaining.isEmpty(); rank++) {
      HudBidder winner = HudBidder.winner(remaining, nowMillis);
      if (winner == null) {
        return Integer.MAX_VALUE;
      }
      if (winner.ownerName().equals(owner) && winner.bid().purpose().equals(laneId)) {
        return rank;
      }
      remaining.remove(winner);
    }
    return Integer.MAX_VALUE;
  }

  private static String laneKey(UUID playerId, String laneId) {
    return playerId + "|" + laneId;
  }

  private static String laneIdOf(String laneKey) {
    int split = laneKey.indexOf('|');
    return split < 0 ? null : laneKey.substring(split + 1);
  }

  @FunctionalInterface
  interface BossBarFactory {
    BossBar create(String title, BarColor color, BarStyle style);
  }

  @FunctionalInterface
  interface SweepScheduler {
    boolean schedule(Runnable runnable, long delayTicks);
  }

  private static final class TrackedBar {
    private final BossBar bar;
    private final long sinceMillis;
    private volatile Player player;
    private volatile BarStyle style;
    private volatile long updatedMillis;
    private volatile long staleMillis;
    private volatile boolean granted;

    private TrackedBar(BossBar bar, Player player, BarStyle style, long sinceMillis) {
      this.bar = bar;
      this.player = player;
      this.style = style;
      this.sinceMillis = sinceMillis;
    }
  }
}
