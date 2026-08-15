package art.arcane.volmlib.util.cache;

import art.arcane.volmlib.util.function.Function2;
import art.arcane.volmlib.util.function.IntIntFunction;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.function.Supplier;

/**
 * Shared world-level cache composed of per-chunk 2D caches.
 */
public class WorldCache2D<T> {
    private final ConcurrentLinkedHashMap<Long, ChunkCache2D<T>> chunks;
    private final IntIntFunction<T> resolver;
    private final Supplier<? extends ChunkCache2D<T>> chunkSupplier;

    public WorldCache2D(Function2<Integer, Integer, T> resolver, Supplier<? extends ChunkCache2D<T>> chunkSupplier) {
        this(resolver, 1024, chunkSupplier);
    }

    public WorldCache2D(Function2<Integer, Integer, T> resolver, int size, Supplier<? extends ChunkCache2D<T>> chunkSupplier) {
        this(size, chunkSupplier, resolver::apply);
    }

    private WorldCache2D(int size, Supplier<? extends ChunkCache2D<T>> chunkSupplier, IntIntFunction<T> resolver) {
        this.resolver = resolver;
        this.chunkSupplier = chunkSupplier;
        chunks = new ConcurrentLinkedHashMap.Builder<Long, ChunkCache2D<T>>()
                .initialCapacity(size)
                .maximumWeightedCapacity(size)
                .concurrencyLevel(Math.max(32, Runtime.getRuntime().availableProcessors() * 4))
                .build();
    }

    /**
     * Boxing-free resolver variant. A static factory rather than an overloaded constructor so that
     * existing lambda call sites of the boxed constructors keep compiling unambiguously.
     */
    public static <T> WorldCache2D<T> ofInts(IntIntFunction<T> resolver, Supplier<? extends ChunkCache2D<T>> chunkSupplier) {
        return ofInts(resolver, 1024, chunkSupplier);
    }

    public static <T> WorldCache2D<T> ofInts(IntIntFunction<T> resolver, int size, Supplier<? extends ChunkCache2D<T>> chunkSupplier) {
        return new WorldCache2D<>(size, chunkSupplier, resolver);
    }

    public T get(int x, int z) {
        long key = CacheKey.key(x >> 4, z >> 4);
        ChunkCache2D<T> chunk = chunkFor(key);
        return chunk.getInts(x, z, resolver);
    }

    public void fillChunk(int chunkX, int chunkZ, Object[] target) {
        if (target == null || target.length != 256) {
            throw new IllegalArgumentException("Expected a 16x16 target array.");
        }

        long key = CacheKey.key(chunkX, chunkZ);
        ChunkCache2D<T> chunk = chunkFor(key);
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;
        chunk.fillInts(worldX, worldZ, target, resolver);
    }

    public long getSize() {
        return chunks.size() * 256L;
    }

    public long getMaxSize() {
        return chunks.capacity() * 256L;
    }

    private ChunkCache2D<T> chunkFor(long key) {
        ChunkCache2D<T> chunk = chunks.get(key);
        if (chunk == null) {
            chunk = chunks.computeIfAbsent(key, ignored -> chunkSupplier.get());
        }

        return chunk;
    }
}
