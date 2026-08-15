package art.arcane.volmlib.util.cache;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.function.ToDoubleBiFunction;

public class WorldCache2DDouble {
    private final ConcurrentLinkedHashMap<Long, ChunkCache2DDouble> chunks;
    private final ToDoubleBiFunction<Integer, Integer> resolver;
    private final ThreadLocal<LastChunkReference<ChunkCache2DDouble>> lastChunk = ThreadLocal.withInitial(LastChunkReference::new);

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
        LastChunkReference<ChunkCache2DDouble> local = lastChunk.get();
        ChunkCache2DDouble chunk = local.get(key);
        if (chunk == null) {
            chunk = chunks.get(key);
            if (chunk == null) {
                chunk = chunks.computeIfAbsent(key, ignored -> new ChunkCache2DDouble());
            }
            local.set(key, chunk);
        }

        return chunk;
    }
}
