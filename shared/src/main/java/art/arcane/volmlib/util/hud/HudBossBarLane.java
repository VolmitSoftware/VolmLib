package art.arcane.volmlib.util.hud;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class HudBossBarLane {
  public static final String METADATA_KEY = "volmit.hud.bossbars";
  private static final String INDEX_KEY = METADATA_KEY + "|index";
  private static final String INDEX_SEPARATOR = ",";

  private final Plugin plugin;
  private final LongSupplier clock;
  private final BossBarFactory bossBars;
  private final SweepScheduler sweeper;
  private final ViewerScheduler viewers;
  private final AtomicLong sweepGeneration = new AtomicLong();
  private final AtomicLong lastSweepMillis = new AtomicLong(Long.MIN_VALUE);
  private final AtomicBoolean sweeping = new AtomicBoolean();
  private final ConcurrentHashMap<String, TrackedBar> bars = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Set<String>> asserted = new ConcurrentHashMap<>();

  public HudBossBarLane() {
    this(null, System::currentTimeMillis, new Runtime(Bukkit::createBossBar,
        (runnable, delayTicks) -> false, (player, runnable, retired) -> {
          runnable.run();
          return true;
        }));
  }

  public HudBossBarLane(Plugin plugin) {
    this(plugin, System::currentTimeMillis, new Runtime(Bukkit::createBossBar,
        (runnable, delayTicks) -> FoliaScheduler.runGlobal(plugin, runnable, delayTicks),
        (player, runnable, retired) -> FoliaScheduler.runEntity(plugin, player, runnable, 0L, retired)));
  }

  HudBossBarLane(Plugin plugin, LongSupplier clock, Runtime runtime) {
    this.plugin = plugin;
    this.clock = clock;
    this.bossBars = runtime.bars();
    this.sweeper = runtime.sweeper();
    this.viewers = runtime.viewers();
  }

  public void show(Player player, String laneId, String title, double progress, BarColor color, BarStyle style, long staleMillis) {
    show(player, laneId, HudPriority.STATUS, title, progress, color, style, staleMillis, Integer.MAX_VALUE);
  }

  public boolean show(Player player, String laneId, int priority, String title, double progress, BarColor color, BarStyle style, long staleMillis, int maxBars) {
    return show(player, laneId, new Options(priority, title, progress, color, style, staleMillis, maxBars, Set.of()));
  }

  public boolean show(Player player, String laneId, Options options) {
    int priority = options.priority();
    String title = options.title();
    double progress = options.progress();
    BarColor color = options.color();
    BarStyle style = options.style();
    long staleMillis = options.staleMillis();
    int maxBars = options.maxBars();
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
      for (BarFlag flag : BarFlag.values()) {
        if (options.flags().contains(flag)) {
          if (!tracked.bar.hasFlag(flag)) {
            tracked.bar.addFlag(flag);
          }
        } else if (tracked.bar.hasFlag(flag)) {
          tracked.bar.removeFlag(flag);
        }
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
    scheduleSweep(Math.max(1L, periodTicks), sweepGeneration.incrementAndGet());
  }

  public void shutdown() {
    sweeping.set(false);
    sweepGeneration.incrementAndGet();
    for (Map.Entry<String, TrackedBar> entry : bars.entrySet()) {
      String key = entry.getKey();
      TrackedBar tracked = entry.getValue();
      if (!bars.remove(key, tracked)) {
        continue;
      }
      Player player = tracked.player;
      viewers.schedule(player, () -> {
        tracked.bar.removeAll();
        if (!bars.containsKey(key)) {
          withdraw(player, laneIdOf(key));
        }
      }, () -> {});
    }
    asserted.clear();
  }

  private void scheduleSweep(long periodTicks, long generation) {
    sweeper.schedule(() -> {
      if (!sweeping.get() || sweepGeneration.get() != generation) {
        return;
      }
      sweep();
      scheduleSweep(periodTicks, generation);
    }, periodTicks);
  }

  private void sweep(long nowMillis) {
    long previous = lastSweepMillis.get();
    if (previous != Long.MIN_VALUE && nowMillis - previous >= 0 && nowMillis - previous < 50
        || !lastSweepMillis.compareAndSet(previous, nowMillis)) {
      return;
    }
    for (Map.Entry<String, TrackedBar> entry : bars.entrySet()) {
      TrackedBar tracked = entry.getValue();
      if (nowMillis - tracked.updatedMillis <= tracked.staleMillis || !tracked.cleanupQueued.compareAndSet(false, true)) {
        continue;
      }
      String key = entry.getKey();
      Player player = tracked.player;
      boolean scheduled = viewers.schedule(player, () -> expire(key, tracked, player),
          () -> retireExpired(key, tracked, player));
      if (!scheduled) {
        tracked.cleanupQueued.set(false);
      }
    }
  }

  private void retireExpired(String key, TrackedBar tracked, Player player) {
    bars.computeIfPresent(key, (ignored, current) -> {
      if (current != tracked || current.player != player) {
        tracked.cleanupQueued.set(false);
        return current;
      }
      Set<String> lanes = asserted.get(player.getUniqueId());
      if (lanes != null) {
        lanes.remove(laneIdOf(key));
      }
      return null;
    });
  }

  private void expire(String key, TrackedBar tracked, Player player) {
    try {
      if (tracked.player != player || clock.getAsLong() - tracked.updatedMillis <= tracked.staleMillis
          || !bars.remove(key, tracked)) {
        return;
      }
      tracked.bar.removeAll();
      withdraw(player, laneIdOf(key));
    } finally {
      tracked.cleanupQueued.set(false);
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

  public record Options(int priority, String title, double progress, BarColor color, BarStyle style,
                        long staleMillis, int maxBars, Set<BarFlag> flags) {
    public Options {
      flags = flags == null ? Set.of() : Set.copyOf(flags);
    }
  }

  record Runtime(BossBarFactory bars, SweepScheduler sweeper, ViewerScheduler viewers) {
  }

  @FunctionalInterface
  interface ViewerScheduler {
    boolean schedule(Player player, Runnable runnable, Runnable retired);
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
    private final AtomicBoolean cleanupQueued = new AtomicBoolean();
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
