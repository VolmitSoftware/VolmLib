package art.arcane.volmlib.util.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class HudSegmentCodec {
  public static final int PROTOCOL_VERSION = 2;
  static final char RECORD_SEPARATOR = '\u001E';
  static final char FIELD_SEPARATOR = '\u001F';

  private HudSegmentCodec() {
  }

  public static String encode(Collection<HudStampedSegment> segments) {
    StringBuilder out = new StringBuilder().append(PROTOCOL_VERSION);
    for (HudStampedSegment segment : segments) {
      out.append(RECORD_SEPARATOR)
        .append(segment.priority()).append(FIELD_SEPARATOR)
        .append(segment.sinceMillis()).append(FIELD_SEPARATOR)
        .append(segment.assertedMillis()).append(FIELD_SEPARATOR)
        .append(segment.ttlMillis()).append(FIELD_SEPARATOR)
        .append(slotCodes(segment.slots())).append(FIELD_SEPARATOR)
        .append(segment.purpose()).append(FIELD_SEPARATOR)
        .append(segment.text());
    }
    return out.toString();
  }

  public static List<HudStampedSegment> decode(String raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    String[] records = raw.split(String.valueOf(RECORD_SEPARATOR));
    if (!records[0].equals(String.valueOf(PROTOCOL_VERSION))) {
      return List.of();
    }
    List<HudStampedSegment> segments = new ArrayList<>(records.length - 1);
    for (int i = 1; i < records.length; i++) {
      HudStampedSegment segment = decodeRecord(records[i]);
      if (segment != null) {
        segments.add(segment);
      }
    }
    return segments;
  }

  private static HudStampedSegment decodeRecord(String record) {
    String[] fields = record.split(String.valueOf(FIELD_SEPARATOR), 7);
    if (fields.length != 7) {
      return null;
    }
    try {
      int priority = Integer.parseInt(fields[0]);
      long sinceMillis = Long.parseLong(fields[1]);
      long assertedMillis = Long.parseLong(fields[2]);
      long ttlMillis = Long.parseLong(fields[3]);
      List<HudSlot> slots = decodeSlots(fields[4]);
      if (slots.isEmpty() || fields[5].isBlank()) {
        return null;
      }
      return new HudStampedSegment(priority, sinceMillis, assertedMillis, ttlMillis, slots, fields[5], fields[6]);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String slotCodes(List<HudSlot> slots) {
    StringBuilder codes = new StringBuilder(slots.size());
    for (HudSlot slot : slots) {
      codes.append(slot.code());
    }
    return codes.toString();
  }

  private static List<HudSlot> decodeSlots(String codes) {
    List<HudSlot> slots = new ArrayList<>(codes.length());
    for (int i = 0; i < codes.length(); i++) {
      HudSlot slot = HudSlot.fromCode(codes.charAt(i));
      if (slot != null && !slots.contains(slot)) {
        slots.add(slot);
      }
    }
    return slots;
  }
}
