package art.arcane.volmlib.util.hud;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HudActionBar {
  public static final String METADATA_KEY = "volmit.hud.segments";

  private final Plugin plugin;
  private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, HudStampedSegment>> segments = new ConcurrentHashMap<>();

  public HudActionBar(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin);
  }

  public void publish(Player player, HudSegment segment) {
    Objects.requireNonNull(player);
    Objects.requireNonNull(segment);
    long now = System.currentTimeMillis();
    ConcurrentHashMap<String, HudStampedSegment> mine = segments.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>());
    HudStampedSegment previous = mine.get(segment.purpose());
    long sinceMillis = previous == null ? now : previous.sinceMillis();
    mine.put(segment.purpose(), new HudStampedSegment(segment.priority(), sinceMillis, now, segment.ttlMillis(), segment.slots(), segment.purpose(), segment.text()));
    post(player, mine, now);
    send(player, composeLine(player, now));
  }

  public void clear(Player player, String purpose) {
    Objects.requireNonNull(player);
    ConcurrentHashMap<String, HudStampedSegment> mine = segments.get(player.getUniqueId());
    if (mine == null || mine.remove(purpose) == null) {
      return;
    }
    long now = System.currentTimeMillis();
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

  public void shutdown() {
    segments.clear();
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
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(line.isEmpty() ? " " : line));
    } catch (Throwable ignored) {
    }
  }
}
