package art.arcane.volmlib.util.hud;

import java.util.List;
import java.util.Objects;

public record HudSegment(String purpose, int priority, long ttlMillis, List<HudSlot> slots, String text) {
  public HudSegment {
    Objects.requireNonNull(purpose);
    Objects.requireNonNull(slots);
    Objects.requireNonNull(text);
    if (purpose.isBlank()) {
      throw new IllegalArgumentException("purpose must not be blank");
    }
    for (int i = 0; i < purpose.length(); i++) {
      if (purpose.charAt(i) < ' ') {
        throw new IllegalArgumentException("purpose must not contain control characters");
      }
    }
    if (ttlMillis <= 0L) {
      throw new IllegalArgumentException("ttlMillis must be positive: " + ttlMillis);
    }
    if (slots.isEmpty()) {
      throw new IllegalArgumentException("slots must not be empty");
    }
    slots = List.copyOf(slots);
    text = sanitize(text);
  }

  private static String sanitize(String text) {
    StringBuilder out = null;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == HudSegmentCodec.RECORD_SEPARATOR || c == HudSegmentCodec.FIELD_SEPARATOR || c == '\n' || c == '\r') {
        if (out == null) {
          out = new StringBuilder(text.length()).append(text, 0, i);
        }
        out.append(' ');
      } else if (out != null) {
        out.append(c);
      }
    }
    return out == null ? text : out.toString();
  }
}
