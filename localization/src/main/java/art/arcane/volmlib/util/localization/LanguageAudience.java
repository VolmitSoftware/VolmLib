package art.arcane.volmlib.util.localization;

import java.util.UUID;
import java.util.function.Supplier;

public final class LanguageAudience {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private LanguageAudience() {
    }

    public static UUID current() {
        return CURRENT.get();
    }

    public static Scope open(UUID playerId) {
        Scope scope = new Scope(CURRENT.get());
        set(playerId);
        return scope;
    }

    public static void run(UUID playerId, Runnable action) {
        UUID previous = CURRENT.get();
        set(playerId);
        try {
            action.run();
        } finally {
            set(previous);
        }
    }

    public static <T> T call(UUID playerId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        set(playerId);
        try {
            return action.get();
        } finally {
            set(previous);
        }
    }

    private static void set(UUID playerId) {
        if (playerId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(playerId);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final UUID previous;
        private boolean closed;

        private Scope(UUID previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                set(previous);
            }
        }
    }
}
