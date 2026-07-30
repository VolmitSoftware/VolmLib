package art.arcane.volmlib.util.hud;

public enum HudSurface {
  ACTION_BAR("volmit.hud.actionbar"),
  TITLE("volmit.hud.title"),
  BOSS_BAR(null);

  private final String metadataKey;

  HudSurface(String metadataKey) {
    this.metadataKey = metadataKey;
  }

  public String metadataKey() {
    return metadataKey;
  }

  public boolean isArbitrated() {
    return metadataKey != null;
  }
}
