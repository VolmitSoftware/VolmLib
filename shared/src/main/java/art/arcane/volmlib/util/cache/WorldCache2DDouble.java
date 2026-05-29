package art.arcane.volmlib.util.cache;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.function.ToDoubleBiFunction;

public class WorldCache2DDouble {
    private final ConcurrentLinkedHashMap<Long, ChunkCache2DDouble> chunks;
    private final ToDoubleBiFunction<Integer, Integer> resolver;
    private final ThreadLocal<LocalChunk> localChunk = ThreadLocal.withInitial(LocalChunk::new);

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
        LocalChunk local = localChunk.get();
        ChunkCache2DDouble chunk = local.chunk;
        if (chunk == null || local.key != key) {
            chunk = chunks.computeIfAbsent(key, $ -> new ChunkCache2DDouble());
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
        ChunkCache2DDouble chunk = chunks.computeIfAbsent(key, $ -> new ChunkCache2DDouble());
        LocalChunk local = localChunk.get();
        local.key = key;
        local.chunk = chunk;
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;
        chunk.fill(worldX, worldZ, target, resolver);
    }

    public void fillChunk(int chunkX, int chunkZ, double[] target) {
        if (target == null || target.length != 256) {
            throw new IllegalArgumentException("Expected a 16x16 target array.");
        }

        long key = CacheKey.key(chunkX, chunkZ);
        ChunkCache2DDouble chunk = chunks.computeIfAbsent(key, $ -> new ChunkCache2DDouble());
        LocalChunk local = localChunk.get();
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

    private static final class LocalChunk {
        private long key = Long.MIN_VALUE;
        private ChunkCache2DDouble chunk;
    }
}
