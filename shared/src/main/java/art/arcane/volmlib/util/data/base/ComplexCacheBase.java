package art.arcane.volmlib.util.data.base;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared cache container keyed by chunk coordinates.
 */
public abstract class ComplexCacheBase<C> {
    private final Map<Long, C> chunks = new ConcurrentHashMap<>();

    protected abstract long toKey(int x, int z);

    protected abstract C createChunk();

    public boolean has(int x, int z) {
        return chunks.containsKey(toKey(x, z));
    }

    public void invalidate(int x, int z) {
        chunks.remove(toKey(x, z));
    }

    public C chunk(int x, int z) {
        return chunks.computeIfAbsent(toKey(x, z), key -> createChunk());
    }
}
