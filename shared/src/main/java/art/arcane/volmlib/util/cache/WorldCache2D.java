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
    private final ThreadLocal<LocalChunk<T>> localChunk = ThreadLocal.withInitial(LocalChunk::new);

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
        long key = CacheKey.key(x >> 4, z >> 4);
        LocalChunk<T> local = localChunk.get();
        ChunkCache2D<T> chunk = local.chunk;
        if (chunk == null || local.key != key) {
            chunk = chunks.computeIfAbsent(key, $ -> chunkSupplier.get());
            local.key = key;
            local.chunk = chunk;
        }

        return chunk.get(x, z, resolver);
    }

    public void fillChunk(int chunkX, int chunkZ, Object[] target) {
        if (target == null || target.length != 256) {
            throw new IllegalArgumentException("Expected a 16x16 target array.");
        }

        long key = CacheKey.key(chunkX, chunkZ);
        ChunkCache2D<T> chunk = chunks.computeIfAbsent(key, $ -> chunkSupplier.get());
        LocalChunk<T> local = localChunk.get();
        local.key = key;
        local.chunk = chunk;
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;
        chunk.fill(worldX, worldZ, target, resolver);
    }

    public long getSize() {
        return chunks.size() * 256L;
    }

    public long getMaxSize() {
        return chunks.capacity() * 256L;
    }

    private static final class LocalChunk<T> {
        private long key = Long.MIN_VALUE;
        private ChunkCache2D<T> chunk;
    }
}
