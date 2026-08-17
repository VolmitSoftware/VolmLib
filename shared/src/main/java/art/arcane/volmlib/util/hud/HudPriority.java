package art.arcane.volmlib.util.hud;

public final class HudPriority {
  public static final int AMBIENT = 10;
  public static final int NOTICE = 30;
  public static final int STATUS = 40;
  public static final int PROGRESS = 60;
  public static final int INTERACTIVE = 80;
  public static final int MODAL = 100;
  public static final int PINNED = 1000;

  private HudPriority() {
  }
}
