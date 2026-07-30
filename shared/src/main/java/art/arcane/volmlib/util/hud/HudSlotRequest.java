package art.arcane.volmlib.util.hud;

import java.util.List;
import java.util.Objects;

public record HudSlotRequest(String purpose, int priority, long ttlMillis, List<HudSurface> preferences) {
  public HudSlotRequest {
    Objects.requireNonNull(purpose);
    Objects.requireNonNull(preferences);
    if (purpose.indexOf('|') >= 0) {
      throw new IllegalArgumentException("purpose must not contain '|': " + purpose);
    }
    if (ttlMillis <= 0L) {
      throw new IllegalArgumentException("ttlMillis must be positive: " + ttlMillis);
    }
    if (preferences.isEmpty()) {
      throw new IllegalArgumentException("preferences must not be empty");
    }
    preferences = List.copyOf(preferences);
  }
}
