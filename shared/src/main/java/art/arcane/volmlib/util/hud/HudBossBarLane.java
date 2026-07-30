package art.arcane.volmlib.util.hud;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HudBossBarLane {
  private final ConcurrentHashMap<String, TrackedBar> bars = new ConcurrentHashMap<>();

  public void show(Player player, String laneId, String title, double progress, BarColor color, BarStyle style, long staleMillis) {
    long now = System.currentTimeMillis();
    TrackedBar tracked = bars.computeIfAbsent(laneKey(player, laneId), key -> new TrackedBar(Bukkit.createBossBar(title, color, style)));
    tracked.bar.setTitle(title);
    tracked.bar.setProgress(Math.max(0.0D, Math.min(1.0D, progress)));
    tracked.bar.setColor(color);
    tracked.bar.addPlayer(player);
    tracked.updatedMillis = now;
    tracked.staleMillis = staleMillis;
    sweep(now);
  }

  public void hide(Player player, String laneId) {
    TrackedBar tracked = bars.remove(laneKey(player, laneId));
    if (tracked != null) {
      tracked.bar.removeAll();
    }
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
  }

  public void shutdown() {
    Iterator<Map.Entry<String, TrackedBar>> iterator = bars.entrySet().iterator();
    while (iterator.hasNext()) {
      iterator.next().getValue().bar.removeAll();
      iterator.remove();
    }
  }

  private void sweep(long nowMillis) {
    Iterator<Map.Entry<String, TrackedBar>> iterator = bars.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, TrackedBar> entry = iterator.next();
      TrackedBar tracked = entry.getValue();
      if (nowMillis - tracked.updatedMillis > tracked.staleMillis) {
        tracked.bar.removeAll();
        iterator.remove();
      }
    }
  }

  private static String laneKey(Player player, String laneId) {
    return player.getUniqueId() + "|" + laneId;
  }

  private static final class TrackedBar {
    private final BossBar bar;
    private volatile long updatedMillis;
    private volatile long staleMillis;

    private TrackedBar(BossBar bar) {
      this.bar = bar;
    }
  }
}
