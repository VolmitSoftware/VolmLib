package art.arcane.volmlib.util.cache;

import art.arcane.volmlib.util.function.Function2;

/**
 * Shared chunk-local 2D cache facade built on the atomic implementation.
 */
public class ChunkCache2D<T> extends ChunkCache2DAtomic<T> {
    public ChunkCache2D(String propertyPrefix) {
        super(propertyPrefix);
    }

    public T get(int x, int z, Function2<Integer, Integer, T> resolver) {
        return getComputed(x, z, resolver::apply);
    }

    public void fill(int worldX, int worldZ, Object[] target, Function2<Integer, Integer, T> resolver) {
        fillComputed(worldX, worldZ, target, resolver::apply);
    }
}
