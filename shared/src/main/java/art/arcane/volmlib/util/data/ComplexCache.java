package art.arcane.volmlib.util.data;

import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.data.base.ComplexCacheBase;

/**
 * Shared chunk-indexed cache implementation.
 */
public class ComplexCache<T> extends ComplexCacheBase<ChunkCache<T>> {
    @Override
    protected long toKey(int x, int z) {
        return CacheKey.key(x, z);
    }

    @Override
    protected ChunkCache<T> createChunk() {
        return new ChunkCache<>();
    }
}
