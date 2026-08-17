package art.arcane.volmlib.util.hud;

import java.util.List;
import java.util.Objects;

public record HudStampedSegment(int priority, long sinceMillis, long assertedMillis, long ttlMillis, List<HudSlot> slots, String purpose, String text) {
  public HudStampedSegment {
    Objects.requireNonNull(slots);
    Objects.requireNonNull(purpose);
    Objects.requireNonNull(text);
    slots = List.copyOf(slots);
  }

  public boolean isExpired(long nowMillis) {
    return nowMillis - assertedMillis > ttlMillis;
  }
}
