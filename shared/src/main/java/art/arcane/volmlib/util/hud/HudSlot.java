package art.arcane.volmlib.util.hud;

public enum HudSlot {
  LEFT('L'),
  CENTER('C'),
  RIGHT('R');

  private final char code;

  HudSlot(char code) {
    this.code = code;
  }

  public char code() {
    return code;
  }

  public static HudSlot fromCode(char code) {
    for (HudSlot slot : values()) {
      if (slot.code == code) {
        return slot;
      }
    }
    return null;
  }
}
