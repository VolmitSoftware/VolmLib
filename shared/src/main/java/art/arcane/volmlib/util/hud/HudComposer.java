package art.arcane.volmlib.util.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

public final class HudComposer {
  public static final int VISIBLE_BUDGET = 150;
  public static final String SEPARATOR = "\u00A7r  ";

  private HudComposer() {
  }

  public record Source(String ownerName, HudStampedSegment segment) {
  }

  public static String compose(List<Source> sources, long nowMillis) {
    List<Source> live = new ArrayList<>(sources.size());
    for (Source source : sources) {
      if (source == null || source.ownerName == null || source.segment == null) {
        continue;
      }
      if (source.segment.isExpired(nowMillis) || source.segment.text().isBlank()) {
        continue;
      }
      live.add(source);
    }
    live.sort(ORDER);
    EnumMap<HudSlot, List<Placed>> lanes = new EnumMap<>(HudSlot.class);
    int visible = 0;
    int index = 0;
    for (Source source : live) {
      String text = source.segment.text();
      int cost = visibleLength(text);
      if (visible > 0 && visible + cost > VISIBLE_BUDGET) {
        continue;
      }
      List<HudSlot> preferences = source.segment.slots();
      HudSlot target = null;
      int rank = preferences.size() - 1;
      for (int i = 0; i < preferences.size(); i++) {
        if (!lanes.containsKey(preferences.get(i))) {
          target = preferences.get(i);
          rank = i;
          break;
        }
      }
      if (target == null) {
        target = preferences.get(preferences.size() - 1);
      }
      lanes.computeIfAbsent(target, key -> new ArrayList<>(2)).add(new Placed(text, rank, index++));
      visible += cost;
    }
    StringBuilder line = new StringBuilder(visible + 8);
    for (HudSlot slot : HudSlot.values()) {
      List<Placed> placed = lanes.get(slot);
      if (placed == null) {
        continue;
      }
      placed.sort(Comparator.comparingInt((Placed p) -> p.rank).thenComparingInt(p -> p.order));
      for (Placed p : placed) {
        if (line.length() > 0) {
          line.append(SEPARATOR);
        }
        line.append(p.text);
      }
    }
    return line.toString();
  }

  static int visibleLength(String text) {
    int length = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\u00A7' && i + 1 < text.length()) {
        i++;
        continue;
      }
      length++;
    }
    return length;
  }

  private static final Comparator<Source> ORDER = (a, b) -> {
    if (a.segment.priority() != b.segment.priority()) {
      return Integer.compare(b.segment.priority(), a.segment.priority());
    }
    if (a.segment.sinceMillis() != b.segment.sinceMillis()) {
      return Long.compare(a.segment.sinceMillis(), b.segment.sinceMillis());
    }
    int owner = a.ownerName.compareTo(b.ownerName);
    if (owner != 0) {
      return owner;
    }
    return a.segment.purpose().compareTo(b.segment.purpose());
  };

  private record Placed(String text, int rank, int order) {
  }
}
