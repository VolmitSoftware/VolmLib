package art.arcane.volmlib.util.cache;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.Arrays;
import java.util.function.ToDoubleBiFunction;

public class WorldCache2DDouble {
    private static final int LOCAL_CHUNK_SLOTS = 1024;

    private final ConcurrentLinkedHashMap<Long, ChunkCache2DDouble> chunks;
    private final ToDoubleBiFunction<Integer, Integer> resolver;
    private final ThreadLocal<LocalChunks> localChunks = ThreadLocal.withInitial(LocalChunks::new);

    public WorldCache2DDouble(ToDoubleBiFunction<Integer, Integer> resolver, int size) {
        this.resolver = resolver;
        this.chunks = new ConcurrentLinkedHashMap.Builder<Long, ChunkCache2DDouble>()
                .initialCapacity(size)
                .maximumWeightedCapacity(size)
                .concurrencyLevel(Math.max(32, Runtime.getRuntime().availableProcessors() * 4))
                .build();
    }

    public double get(int x, int z) {
        long key = CacheKey.key(x >> 4, z >> 4);
        ChunkCache2DDouble chunk = chunkFor(key);
        return chunk.get(x, z, resolver);
    }

    public void fillChunk(int chunkX, int chunkZ, Object[] target) {
        if (target == null || target.length != 256) {
            throw new IllegalArgumentException("Expected a 16x16 target array.");
        }

        long key = CacheKey.key(chunkX, chunkZ);
        ChunkCache2DDouble chunk = chunkFor(key);
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;
        chunk.fill(worldX, worldZ, target, resolver);
    }

    public void fillChunk(int chunkX, int chunkZ, double[] target) {
        if (target == null || target.length != 256) {
            throw new IllegalArgumentException("Expected a 16x16 target array.");
        }

        long key = CacheKey.key(chunkX, chunkZ);
        ChunkCache2DDouble chunk = chunkFor(key);
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

    private ChunkCache2DDouble chunkFor(long key) {
        LocalChunks local = localChunks.get();
        ChunkCache2DDouble chunk = local.get(key);
        if (chunk == null) {
            chunk = chunks.get(key);
            if (chunk == null) {
                chunk = chunks.computeIfAbsent(key, $ -> new ChunkCache2DDouble());
            }
            local.put(key, chunk);
        }

        return chunk;
    }

    private static final class LocalChunks {
        private final long[] keys = new long[LOCAL_CHUNK_SLOTS];
        private final ChunkCache2DDouble[] chunks = new ChunkCache2DDouble[LOCAL_CHUNK_SLOTS];

        private LocalChunks() {
            Arrays.fill(keys, Long.MIN_VALUE);
        }

        private ChunkCache2DDouble get(long key) {
            int slot = slot(key);
            if (keys[slot] == key) {
                return chunks[slot];
            }

            return null;
        }

        private void put(long key, ChunkCache2DDouble chunk) {
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
