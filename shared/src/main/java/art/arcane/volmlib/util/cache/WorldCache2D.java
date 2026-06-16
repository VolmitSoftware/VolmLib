package art.arcane.volmlib.util.cache;

import art.arcane.volmlib.util.function.Function2;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Shared world-level cache composed of per-chunk 2D caches.
 */
public class WorldCache2D<T> {
    private static final int LOCAL_CHUNK_SLOTS = 1024;

    private final ConcurrentLinkedHashMap<Long, ChunkCache2D<T>> chunks;
    private final Function2<Integer, Integer, T> resolver;
    private final Supplier<? extends ChunkCache2D<T>> chunkSupplier;
    private final ThreadLocal<LocalChunks<T>> localChunks = ThreadLocal.withInitial(LocalChunks::new);

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
        ChunkCache2D<T> chunk = chunkFor(key);
        return chunk.get(x, z, resolver);
    }

    public void fillChunk(int chunkX, int chunkZ, Object[] target) {
        if (target == null || target.length != 256) {
            throw new IllegalArgumentException("Expected a 16x16 target array.");
        }

        long key = CacheKey.key(chunkX, chunkZ);
        ChunkCache2D<T> chunk = chunkFor(key);
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

    private ChunkCache2D<T> chunkFor(long key) {
        LocalChunks<T> local = localChunks.get();
        ChunkCache2D<T> chunk = local.get(key);
        if (chunk == null) {
            chunk = chunks.get(key);
            if (chunk == null) {
                chunk = chunks.computeIfAbsent(key, $ -> chunkSupplier.get());
            }
            local.put(key, chunk);
        }

        return chunk;
    }

    private static final class LocalChunks<T> {
        private final long[] keys = new long[LOCAL_CHUNK_SLOTS];
        private final ChunkCache2D<T>[] chunks;

        @SuppressWarnings("unchecked")
        private LocalChunks() {
            Arrays.fill(keys, Long.MIN_VALUE);
            chunks = (ChunkCache2D<T>[]) new ChunkCache2D[LOCAL_CHUNK_SLOTS];
        }

        private ChunkCache2D<T> get(long key) {
            int slot = slot(key);
            if (keys[slot] == key) {
                return chunks[slot];
            }

            return null;
        }

        private void put(long key, ChunkCache2D<T> chunk) {
            int slot = slot(key);
            keys[slot] = key;
            chunks[slot] = chunk;
        }

        private int slot(long key) {
            long mixed = key;
            mixed ^= mixed >>> 33;
            mixed *= 0xff51afd7ed558ccdL;
            mixed ^= mixed >>> 33;
            mixed *= 0xc4ceb9fe1a85ec53L;
            mixed ^= mixed >>> 33;
            return (int) mixed & (LOCAL_CHUNK_SLOTS - 1);
        }
    }
}
