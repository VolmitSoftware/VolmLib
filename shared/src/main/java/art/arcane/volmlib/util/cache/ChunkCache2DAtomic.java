package art.arcane.volmlib.util.cache;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.BiFunction;

/**
 * Atomic 2D chunk-local cache with optional fast/dynamic modes controlled by system properties:
 * {@code <prefix>.cache.fast} and {@code <prefix>.cache.dynamic}.
 */
public class ChunkCache2DAtomic<T> {
    private static final VarHandle VALUE_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle STATE_HANDLE = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final byte STATE_EMPTY = 0;
    private static final byte STATE_COMPUTING = 1;
    private static final byte STATE_READY = 2;

    private final boolean fast;
    private final boolean dynamic;
    private final Object[] values;
    private final byte[] states;

    public ChunkCache2DAtomic(String propertyPrefix) {
        this.fast = Boolean.getBoolean(propertyPrefix + ".cache.fast");
        this.dynamic = Boolean.getBoolean(propertyPrefix + ".cache.dynamic");
        this.values = new Object[256];
        this.states = new byte[256];
    }

    @SuppressWarnings("unchecked")
    protected T getComputed(int x, int z, BiFunction<Integer, Integer, T> resolver) {
        int key = ((z & 15) << 4) | (x & 15);
        Object cached = VALUE_HANDLE.getAcquire(values, key);
        if (cached != null) {
            return (T) cached;
        }

        return compute(key, x, z, resolver);
    }

    protected void fillComputed(int worldX, int worldZ, Object[] target, BiFunction<Integer, Integer, T> resolver) {
        for (int row = 0; row < 16; row++) {
            int rowOffset = row << 4;
            int sampleZ = worldZ + row;
            for (int column = 0; column < 16; column++) {
                int key = rowOffset + column;
                Object cached = VALUE_HANDLE.getAcquire(values, key);
                if (cached != null) {
                    target[key] = cached;
                    continue;
                }

                target[key] = compute(key, worldX + column, sampleZ, resolver);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private T compute(int key, int x, int z, BiFunction<Integer, Integer, T> resolver) {
        if (fast) {
            T resolvedFast = resolver.apply(x, z);
            if (resolvedFast != null) {
                VALUE_HANDLE.compareAndSet(values, key, null, resolvedFast);
                STATE_HANDLE.compareAndSet(states, key, STATE_EMPTY, STATE_READY);
            }

            return resolvedFast;
        }

        int spins = dynamic ? 8 : 32;
        while (true) {
            Object cached = VALUE_HANDLE.getAcquire(values, key);
            if (cached != null) {
                return (T) cached;
            }

            byte state = (byte) STATE_HANDLE.getAcquire(states, key);
            if (state == STATE_EMPTY && STATE_HANDLE.compareAndSet(states, key, STATE_EMPTY, STATE_COMPUTING)) {
                T resolved = resolver.apply(x, z);
                if (resolved != null) {
                    VALUE_HANDLE.setRelease(values, key, resolved);
                    STATE_HANDLE.setRelease(states, key, STATE_READY);
                } else {
                    STATE_HANDLE.setRelease(states, key, STATE_EMPTY);
                }

                return resolved;
            }

            if (spins > 0) {
                spins--;
                Thread.onSpinWait();
                continue;
            }

            Thread.yield();
        }
    }
}
