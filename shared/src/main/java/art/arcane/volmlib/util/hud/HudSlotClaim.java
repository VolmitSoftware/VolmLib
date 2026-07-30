package art.arcane.volmlib.util.hud;

import org.bukkit.entity.Player;

public final class HudSlotClaim {
  private final HudSlotService service;
  private final Player player;
  private final HudSlotRequest request;
  private final long sessionId;
  private final long sinceMillis;
  private volatile HudSurface granted;

  HudSlotClaim(HudSlotService service, Player player, HudSlotRequest request, long sessionId, long sinceMillis) {
    this.service = service;
    this.player = player;
    this.request = request;
    this.sessionId = sessionId;
    this.sinceMillis = sinceMillis;
  }

  public HudSurface resolve() {
    HudSurface result = service.resolve(player, request, sessionId, sinceMillis);
    granted = result;
    return result;
  }

  public HudSurface granted() {
    return granted;
  }

  public void release() {
    granted = null;
    service.releaseAll(player, request, sessionId);
  }
}
