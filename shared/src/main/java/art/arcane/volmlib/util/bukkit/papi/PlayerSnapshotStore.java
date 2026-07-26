package art.arcane.volmlib.util.bukkit.papi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSnapshotStore<T> {
    public static final long DEFAULT_GRACE_MS = 60_000L;

    private final Map<UUID, Held<T>> held = new ConcurrentHashMap<>();

    public void publish(UUID playerId, T snapshot) {
        if (playerId == null) {
            return;
        }

        if (snapshot == null) {
            held.remove(playerId);
            return;
        }

        held.put(playerId, new Held<>(snapshot));
    }

    public T get(UUID playerId) {
        if (playerId == null) {
            return null;
        }

        Held<T> entry = held.get(playerId);

        if (entry == null) {
            return null;
        }

        long expiresAtMs = entry.expiresAtMs;

        if (expiresAtMs != 0L && System.currentTimeMillis() >= expiresAtMs) {
            held.remove(playerId, entry);
            return null;
        }

        return entry.value;
    }

    public boolean isPresent(UUID playerId) {
        return get(playerId) != null;
    }

    public String available(UUID playerId) {
        return PlaceholderValues.bool(isPresent(playerId));
    }

    public void evictAfterGrace(UUID playerId, long graceMs) {
        if (playerId == null) {
            return;
        }

        if (graceMs <= 0L) {
            held.remove(playerId);
            return;
        }

        Held<T> entry = held.get(playerId);

        if (entry != null) {
            entry.expiresAtMs = System.currentTimeMillis() + graceMs;
        }
    }

    public void clear() {
        held.clear();
    }

    private static final class Held<T> {
        private final T value;
        private volatile long expiresAtMs;

        private Held(T value) {
            this.value = value;
        }
    }
}
