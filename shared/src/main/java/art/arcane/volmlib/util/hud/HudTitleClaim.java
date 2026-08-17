package art.arcane.volmlib.util.hud;

import org.bukkit.entity.Player;

import java.util.UUID;

public final class HudTitleClaim {
  private final HudTitleService service;
  private final Player player;
  private final UUID playerId;
  private final String purpose;
  private final int priority;
  private final long ttlMillis;
  private final long sessionId;
  private final long sinceMillis;
  private volatile boolean granted;

  HudTitleClaim(HudTitleService service, Player player, String purpose, int priority, long ttlMillis, long sessionId, long sinceMillis) {
    this.service = service;
    this.player = player;
    this.playerId = player.getUniqueId();
    this.purpose = purpose;
    this.priority = priority;
    this.ttlMillis = ttlMillis;
    this.sessionId = sessionId;
    this.sinceMillis = sinceMillis;
  }

  public boolean resolve() {
    boolean result = service.resolve(player, purpose, priority, ttlMillis, sessionId, sinceMillis);
    granted = result;
    return result;
  }

  public boolean granted() {
    return granted;
  }

  public void release() {
    granted = false;
    service.release(player, sessionId);
  }

  public void retire() {
    granted = false;
    service.retire(playerId, sessionId);
  }
}
