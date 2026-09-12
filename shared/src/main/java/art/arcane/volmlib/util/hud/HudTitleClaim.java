package art.arcane.volmlib.util.hud;

import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

public final class HudTitleClaim {
  private static final long TICK_MILLIS = 50L;

  private final HudTitleService service;
  private final Player player;
  private final UUID playerId;
  private final String purpose;
  private final int priority;
  private final long ttlMillis;
  private final long sessionId;
  private final long sinceMillis;
  private final Consumer<HudTitleClaim> onPreempted;
  private volatile boolean granted;

  HudTitleClaim(HudTitleService service, Player player, String purpose, int priority, long ttlMillis, long sessionId, long sinceMillis, Consumer<HudTitleClaim> onPreempted) {
    this.service = service;
    this.player = player;
    this.playerId = player.getUniqueId();
    this.purpose = purpose;
    this.priority = priority;
    this.ttlMillis = ttlMillis;
    this.sessionId = sessionId;
    this.sinceMillis = sinceMillis;
    this.onPreempted = onPreempted;
  }

  public boolean resolve() {
    boolean result = service.resolve(this);
    granted = result;
    return result;
  }

  public boolean show(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
    if (!resolve()) {
      return false;
    }
    ComponentMessenger.showTitle(
        player,
        ComponentText.section(title),
        ComponentText.section(subtitle),
        Duration.ofMillis(Math.max(0, fadeInTicks) * TICK_MILLIS),
        Duration.ofMillis(Math.max(0, stayTicks) * TICK_MILLIS),
        Duration.ofMillis(Math.max(0, fadeOutTicks) * TICK_MILLIS));
    return true;
  }

  public boolean granted() {
    return granted;
  }

  public String purpose() {
    return purpose;
  }

  public void release() {
    granted = false;
    service.release(player, sessionId);
  }

  public boolean dismiss() {
    granted = false;
    return service.dismiss(player, sessionId, purpose, sinceMillis);
  }

  public void retire() {
    granted = false;
    service.retire(playerId, sessionId);
  }

  void preempt() {
    granted = false;
    onPreempted.accept(this);
  }

  Player player() {
    return player;
  }

  UUID playerId() {
    return playerId;
  }

  int priority() {
    return priority;
  }

  long ttlMillis() {
    return ttlMillis;
  }

  long sessionId() {
    return sessionId;
  }

  long sinceMillis() {
    return sinceMillis;
  }
}
