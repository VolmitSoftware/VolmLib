package art.arcane.volmlib.util.cache;

import java.lang.ref.WeakReference;

final class LastChunkReference<T> {
    private long key;
    private WeakReference<T> reference;

    T get(long requestedKey) {
        if (reference == null || key != requestedKey) {
            return null;
        }

        return reference.get();
    }

    void set(long key, T value) {
        this.key = key;
        this.reference = new WeakReference<>(value);
    }
}
