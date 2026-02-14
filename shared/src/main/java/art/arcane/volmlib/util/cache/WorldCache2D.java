package art.arcane.volmlib.util.cache;

import art.arcane.volmlib.util.function.Function2;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.function.Supplier;

/**
 * Shared world-level cache composed of per-chunk 2D caches.
 */
public class WorldCache2D<T> {
    private final ConcurrentLinkedHashMap<Long, ChunkCache2D<T>> chunks;
    private final Function2<Integer, Integer, T> resolver;
    private final Supplier<? extends ChunkCache2D<T>> chunkSupplier;

    public WorldCache2D(Function2<Integer, Integer, T> resolver, Supplier<? extends ChunkCache2D<T>> chunkSupplier) {
        this(resolver, 1024, chunkSupplier);
    }

    public WorldCache2D(Function2<Integer, Integer, T> resolver, int size, Supplier<? extends ChunkCache2D<T>> chunkSupplier) {
        this.resolver = resolver;
        this.chunkSupplier = chunkSupplier;
        chunks = new ConcurrentLinkedHashMap.Builder<Long, ChunkCache2D<T>>()
                .initialCapacity(size)
                .maximumWeightedCapacity(size)
                .concurrencyLevel(Math.max(32, Runtime.getRuntime().availableProcessors() * 4))
                .build();
    }

    public T get(int x, int z) {
        ChunkCache2D<T> chunk = chunks.computeIfAbsent(CacheKey.key(x >> 4, z >> 4), $ -> chunkSupplier.get());
        return chunk.get(x, z, resolver);
    }

    public long getSize() {
        return chunks.size() * 256L;
    }

    public long getMaxSize() {
        return chunks.capacity() * 256L;
    }
}
