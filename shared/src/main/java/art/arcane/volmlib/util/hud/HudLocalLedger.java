package art.arcane.volmlib.util.hud;

import java.util.concurrent.ConcurrentHashMap;

final class HudLocalLedger {
  private static final long SWEEP_GRACE_MILLIS = 60_000L;

  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

  boolean claim(String key, long sessionId, int priority, long sinceMillis, long ttlMillis, long nowMillis) {
    Entry stored = entries.compute(key, (k, existing) -> {
      if (existing == null || existing.sessionId() == sessionId || existing.isExpired(nowMillis)) {
        return new Entry(sessionId, priority, sinceMillis, nowMillis, ttlMillis);
      }
      if (beats(priority, sinceMillis, sessionId, existing)) {
        return new Entry(sessionId, priority, sinceMillis, nowMillis, ttlMillis);
      }
      return existing;
    });
    return stored.sessionId() == sessionId;
  }

  boolean release(String key, long sessionId) {
    boolean[] removed = new boolean[1];
    entries.computeIfPresent(key, (k, existing) -> {
      if (existing.sessionId() == sessionId) {
        removed[0] = true;
        return null;
      }
      return existing;
    });
    return removed[0];
  }

  void sweep(long nowMillis) {
    entries.entrySet().removeIf(entry -> nowMillis - entry.getValue().assertedMillis() > entry.getValue().ttlMillis() + SWEEP_GRACE_MILLIS);
  }

  void clearPrefix(String keyPrefix) {
    entries.keySet().removeIf(key -> key.startsWith(keyPrefix));
  }

  void clear() {
    entries.clear();
  }

  private static boolean beats(int priority, long sinceMillis, long sessionId, Entry holder) {
    if (priority != holder.priority()) {
      return priority > holder.priority();
    }
    if (sinceMillis != holder.sinceMillis()) {
      return sinceMillis < holder.sinceMillis();
    }
    return sessionId < holder.sessionId();
  }

  private record Entry(long sessionId, int priority, long sinceMillis, long assertedMillis, long ttlMillis) {
    boolean isExpired(long nowMillis) {
      return nowMillis - assertedMillis > ttlMillis;
    }
  }
}
