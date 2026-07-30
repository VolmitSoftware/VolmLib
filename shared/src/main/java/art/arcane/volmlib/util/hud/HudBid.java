package art.arcane.volmlib.util.hud;

public record HudBid(int priority, long sinceMillis, long assertedMillis, long ttlMillis, String purpose) {
  public static final int PROTOCOL_VERSION = 1;

  public boolean isExpired(long nowMillis) {
    return nowMillis - assertedMillis > ttlMillis;
  }

  public String encode() {
    return PROTOCOL_VERSION + "|" + priority + "|" + sinceMillis + "|" + assertedMillis + "|" + ttlMillis + "|" + purpose;
  }

  public static HudBid decode(String raw) {
    if (raw == null) {
      return null;
    }
    String[] parts = raw.split("\\|", 6);
    if (parts.length != 6) {
      return null;
    }
    try {
      if (Integer.parseInt(parts[0]) != PROTOCOL_VERSION) {
        return null;
      }
      return new HudBid(Integer.parseInt(parts[1]), Long.parseLong(parts[2]), Long.parseLong(parts[3]), Long.parseLong(parts[4]), parts[5]);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
