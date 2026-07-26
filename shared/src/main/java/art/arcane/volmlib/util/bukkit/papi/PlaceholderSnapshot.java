package art.arcane.volmlib.util.bukkit.papi;

public final class PlaceholderSnapshot<T> {
    private volatile long publishedAtMs;
    private volatile T value;

    public T get() {
        return value;
    }

    public long publishedAtMs() {
        return publishedAtMs;
    }

    public void publish(T snapshot) {
        publishedAtMs = System.currentTimeMillis();
        value = snapshot;
    }

    public boolean isPresent() {
        return value != null;
    }

    public String available() {
        return PlaceholderValues.bool(isPresent());
    }
}
