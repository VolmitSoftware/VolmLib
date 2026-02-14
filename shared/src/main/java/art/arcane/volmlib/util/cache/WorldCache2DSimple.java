package art.arcane.volmlib.util.cache;

import java.util.function.BiFunction;

public abstract class WorldCache2DSimple<T, C> {
    private final BiFunction<Integer, Integer, T> resolver;

    protected WorldCache2DSimple(BiFunction<Integer, Integer, T> resolver) {
        this.resolver = resolver;
    }

    protected abstract C chunkAt(int chunkX, int chunkZ);

    protected abstract T resolve(C chunk, int x, int z, BiFunction<Integer, Integer, T> resolver);

    protected abstract long chunkCount();

    public T get(int x, int z) {
        C chunk = chunkAt(x >> 4, z >> 4);
        return resolve(chunk, x, z, resolver);
    }

    public long getSize() {
        return chunkCount() * 256L;
    }
}
