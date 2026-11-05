package art.arcane.volmlib.nativelib.terrain;

/**
 * Lets the generator decide what one failed native placement means. The registered handler returns to skip the
 * placement, or throws to stop the chunk. With no handler every failure stops the chunk.
 */
public final class NativePlacementFailures {
    private static volatile Handler handler;

    private NativePlacementFailures() {
    }

    public static void register(Handler registered) {
        handler = registered;
    }

    public static void skip(Throwable failure, String kind, String id, int x, int z) {
        Handler current = handler;
        if (current != null) {
            current.skip(failure, kind, id, x, z);
            return;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(kind + " " + id + " failed at " + x + "," + z, failure);
    }

    @FunctionalInterface
    public interface Handler {
        void skip(Throwable failure, String kind, String id, int x, int z);
    }
}
