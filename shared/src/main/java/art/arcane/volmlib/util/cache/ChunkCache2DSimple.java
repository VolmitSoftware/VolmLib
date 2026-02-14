package art.arcane.volmlib.util.cache;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiFunction;

public class ChunkCache2DSimple<T> {
    private final AtomicReferenceArray<T> cache = new AtomicReferenceArray<>(256);

    protected T getComputed(int x, int z, BiFunction<Integer, Integer, T> resolver) {
        int key = ((z & 15) * 16) + (x & 15);
        T value = cache.get(key);

        if (value == null) {
            value = resolver.apply(x, z);
            cache.set(key, value);
        }

        return value;
    }
}
