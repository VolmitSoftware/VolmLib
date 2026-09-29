package art.arcane.volmlib.util.cache;

import art.arcane.volmlib.util.function.IntIntToDoubleFunction;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

public class WorldCache2DDouble {
    private static final int RECENT_KEY_SLOTS = 64;
    private static final int MAXIMUM_REFRESH_INTERVAL = 64;

    private final ConcurrentLinkedHashMap<Long, ChunkCache2DDouble> chunks;
    private final int refreshInterval;
    private final IntIntToDoubleFunction resolver;
    private final ThreadLocal<RecentChunk> recent = ThreadLocal.withInitial(RecentChunk::new);

    public WorldCache2DDouble(IntIntToDoubleFunction resolver, int size) {
        this.resolver = resolver;
        this.refreshInterval = Math.max(1, Math.min(MAXIMUM_REFRESH_INTERVAL, size / 4));
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

    public void setMaximumChunks(int maximumChunks) {
        if (maximumChunks <= 0) {
            throw new IllegalArgumentException("maximumChunks must be positive.");
        }
        chunks.setCapacity(maximumChunks);
    }

    private ChunkCache2DDouble chunkFor(long key) {
        RecentChunk recent = this.recent.get();
        if (recent.chunk != null && recent.key == key) {
            return recent.chunk;
        }
        long mixedKey = CacheKey.mix(key);
        ChunkCache2DDouble chunk = recent.refresh(mixedKey, refreshInterval)
                ? chunks.get(mixedKey) : chunks.getQuietly(mixedKey);
        if (chunk == null) {
            chunk = chunks.computeIfAbsent(mixedKey, ignored -> new ChunkCache2DDouble());
        }
        recent.key = key;
        recent.chunk = chunk;
        return chunk;
    }

    private static final class RecentChunk {
        private final long[] keys = new long[RECENT_KEY_SLOTS];
        private final long[] refreshedAt = new long[RECENT_KEY_SLOTS];
        private long key;
        private ChunkCache2DDouble chunk;
        private long accesses;

        private boolean refresh(long key, int interval) {
            long current = ++accesses;
            int slot = (int) key & (RECENT_KEY_SLOTS - 1);
            if (refreshedAt[slot] != 0L && keys[slot] == key && current - refreshedAt[slot] < interval) {
                return false;
            }
            keys[slot] = key;
            refreshedAt[slot] = current;
            return true;
        }
    }
}
