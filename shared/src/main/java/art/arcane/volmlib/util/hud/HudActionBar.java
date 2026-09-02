package art.arcane.volmlib.util.hud;

import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class HudActionBar {
  public static final String METADATA_KEY = "volmit.hud.segments";
  private static final long CLIENT_REFRESH_MILLIS = 2_000L;
  private static final long TICK_MILLIS = 50L;

  private final Plugin plugin;
  private final LongSupplier clock;
  private final LifecycleScheduler lifecycleScheduler;
  private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, HudStampedSegment>> segments = new ConcurrentHashMap<>();

  public HudActionBar(Plugin plugin) {
    this(plugin, System::currentTimeMillis,
        (player, runnable, delayTicks, retired) -> FoliaScheduler.runEntity(plugin, player, runnable, delayTicks, retired));
  }

  HudActionBar(Plugin plugin, LongSupplier clock, LifecycleScheduler lifecycleScheduler) {
    this.plugin = Objects.requireNonNull(plugin);
    this.clock = Objects.requireNonNull(clock);
    this.lifecycleScheduler = Objects.requireNonNull(lifecycleScheduler);
  }

  public void publish(Player player, HudSegment segment) {
    Objects.requireNonNull(player);
    Objects.requireNonNull(segment);
    long now = clock.getAsLong();
    ConcurrentHashMap<String, HudStampedSegment> mine = segments.compute(player.getUniqueId(), (key, current) -> {
      ConcurrentHashMap<String, HudStampedSegment> updated = current == null ? new ConcurrentHashMap<>() : current;
      HudStampedSegment previous = updated.get(segment.purpose());
      long sinceMillis = previous == null ? now : previous.sinceMillis();
      updated.put(segment.purpose(), new HudStampedSegment(segment.priority(), sinceMillis, now, segment.ttlMillis(), segment.slots(), segment.purpose(), segment.text()));
      return updated;
    });
    post(player, mine, now);
    send(player, composeLine(player, now));
    scheduleLifecycle(player, mine.get(segment.purpose()), now);
  }

  public void clear(Player player, String purpose) {
    Objects.requireNonNull(player);
    ConcurrentHashMap<String, HudStampedSegment> mine = segments.get(player.getUniqueId());
    if (mine == null || mine.remove(purpose) == null) {
      return;
    }
    long now = clock.getAsLong();
    if (mine.isEmpty()) {
      segments.remove(player.getUniqueId());
      player.removeMetadata(METADATA_KEY, plugin);
    } else {
      post(player, mine, now);
    }
    send(player, composeLine(player, now));
  }

  public void clearAll(Player player) {
    segments.remove(player.getUniqueId());
    player.removeMetadata(METADATA_KEY, plugin);
  }

  public void retire(UUID playerId) {
    segments.remove(playerId);
  }

  public void retire(UUID playerId, String purpose) {
    Objects.requireNonNull(playerId);
    Objects.requireNonNull(purpose);
    segments.computeIfPresent(playerId, (key, mine) -> {
      mine.remove(purpose);
      return mine.isEmpty() ? null : mine;
    });
  }

  public void shutdown() {
    segments.clear();
  }

  private void scheduleLifecycle(Player player, HudStampedSegment expected, long nowMillis) {
    if (expected == null) {
      return;
    }
    long elapsedMillis = Math.max(0L, nowMillis - expected.assertedMillis());
    long remainingMillis = Math.max(1L, expected.ttlMillis() - elapsedMillis);
    long delayMillis = Math.min(CLIENT_REFRESH_MILLIS, remainingMillis);
    long delayTicks = Math.max(1L, (delayMillis + TICK_MILLIS - 1L) / TICK_MILLIS);
    UUID playerId = player.getUniqueId();
    lifecycleScheduler.schedule(
        player,
        () -> refresh(player, expected),
        delayTicks,
        () -> retire(playerId, expected.purpose())
    );
  }

  private void refresh(Player player, HudStampedSegment expected) {
    ConcurrentHashMap<String, HudStampedSegment> mine = segments.get(player.getUniqueId());
    if (mine == null || mine.get(expected.purpose()) != expected) {
      return;
    }

    long now = clock.getAsLong();
    if (expected.isExpired(now)) {
      clear(player, expected.purpose());
      return;
    }

    post(player, mine, now);
    send(player, composeLine(player, now));
    scheduleLifecycle(player, expected, now);
  }

  private void post(Player player, ConcurrentHashMap<String, HudStampedSegment> mine, long nowMillis) {
    mine.values().removeIf(segment -> segment.isExpired(nowMillis));
    if (mine.isEmpty()) {
      player.removeMetadata(METADATA_KEY, plugin);
      return;
    }
    player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, HudSegmentCodec.encode(mine.values())));
  }

  private String composeLine(Player player, long nowMillis) {
    List<HudComposer.Source> sources = new ArrayList<>();
    for (MetadataValue value : player.getMetadata(METADATA_KEY)) {
      if (value.getOwningPlugin() == null) {
        continue;
      }
      String owner = value.getOwningPlugin().getName();
      for (HudStampedSegment segment : HudSegmentCodec.decode(value.asString())) {
        sources.add(new HudComposer.Source(owner, segment));
      }
    }
    return HudComposer.compose(sources, nowMillis);
  }

  private static void send(Player player, String line) {
    try {
      ComponentMessenger.sendActionBarSection(player, line.isEmpty() ? " " : line);
    } catch (Throwable ignored) {
    }
  }

  @FunctionalInterface
  interface LifecycleScheduler {
    boolean schedule(Player player, Runnable runnable, long delayTicks, Runnable retired);
  }
}
