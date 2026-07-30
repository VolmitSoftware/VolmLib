package art.arcane.volmlib.util.cache;

import art.arcane.volmlib.util.function.Function2;
import art.arcane.volmlib.util.function.IntIntFunction;

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

    /**
     * Boxing-free variant of {@link #get(int, int, Function2)}. Named rather than overloaded so
     * existing lambda call sites of the boxed methods keep compiling unambiguously.
     */
    public T getInts(int x, int z, IntIntFunction<T> resolver) {
        return getComputedInts(x, z, resolver);
    }

    /**
     * Boxing-free variant of {@link #fill(int, int, Object[], Function2)}.
     */
    public void fillInts(int worldX, int worldZ, Object[] target, IntIntFunction<T> resolver) {
        fillComputedInts(worldX, worldZ, target, resolver);
    }
}
