package art.arcane.volmlib.util.cache;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.BiFunction;

/**
 * Atomic 2D chunk-local cache with optional fast/dynamic modes controlled by system properties:
 * {@code <prefix>.cache.fast} and {@code <prefix>.cache.dynamic}.
 */
public class ChunkCache2DAtomic<T> {
    private static final VarHandle AA = MethodHandles.arrayElementVarHandle(Entry[].class);

    private final boolean fast;
    private final boolean dynamic;
    private final Entry<T>[] cache;

    @SuppressWarnings("unchecked")
    public ChunkCache2DAtomic(String propertyPrefix) {
        this.fast = Boolean.getBoolean(propertyPrefix + ".cache.fast");
        this.dynamic = Boolean.getBoolean(propertyPrefix + ".cache.dynamic");
        this.cache = new Entry[256];

        if (dynamic) {
            return;
        }

        for (int i = 0; i < cache.length; i++) {
            cache[i] = fast ? new FastEntry<>() : new Entry<>();
        }
    }

    @SuppressWarnings("unchecked")
    protected T getComputed(int x, int z, BiFunction<Integer, Integer, T> resolver) {
        int key = ((z & 15) * 16) + (x & 15);
        Entry<T> entry = cache[key];

        if (entry == null) {
            entry = fast ? new FastEntry<>() : new Entry<>();
            if (!AA.compareAndSet(cache, key, null, entry)) {
                entry = (Entry<T>) AA.getVolatile(cache, key);
            }
        }

        return entry.compute(x, z, resolver);
    }

    private static class Entry<T> {
        protected volatile T value;

        protected T compute(int x, int z, BiFunction<Integer, Integer, T> resolver) {
            if (value != null) {
                return value;
            }

            synchronized (this) {
                if (value == null) {
                    value = resolver.apply(x, z);
                }
                return value;
            }
        }
    }

    private static class FastEntry<T> extends Entry<T> {
        @Override
        protected T compute(int x, int z, BiFunction<Integer, Integer, T> resolver) {
            if (value != null) {
                return value;
            }

            return value = resolver.apply(x, z);
        }
    }
}
