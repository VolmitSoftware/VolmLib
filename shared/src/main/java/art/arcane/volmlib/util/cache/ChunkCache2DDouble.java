package art.arcane.volmlib.util.cache;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.ToDoubleBiFunction;

/**
 * A 16 by 16 column cache of primitive doubles. Each column is claimed with a per-slot state, so
 * threads working on different columns of one chunk never wait on each other, and a thread that
 * finds another one computing its column samples the pure resolver itself instead of waiting.
 */
public class ChunkCache2DDouble {
    private static final VarHandle STATE_HANDLE = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final VarHandle VALUE_HANDLE = MethodHandles.arrayElementVarHandle(double[].class);
    private static final byte STATE_EMPTY = 0;
    private static final byte STATE_COMPUTING = 1;
    private static final byte STATE_READY = 2;

    private final byte[] states = new byte[256];
    private final double[] values = new double[256];

    public double get(int x, int z, ToDoubleBiFunction<Integer, Integer> resolver) {
        int key = ((z & 15) << 4) | (x & 15);
        if (((byte) STATE_HANDLE.getAcquire(states, key)) == STATE_READY) {
            return (double) VALUE_HANDLE.getVolatile(values, key);
        }

        return compute(key, x, z, resolver);
    }

    public void fill(int worldX, int worldZ, Object[] target, ToDoubleBiFunction<Integer, Integer> resolver) {
        for (int row = 0; row < 16; row++) {
            int rowOffset = row << 4;
            int sampleZ = worldZ + row;
            for (int column = 0; column < 16; column++) {
                int key = rowOffset + column;
                target[key] = resolved(key, worldX + column, sampleZ, resolver);
            }
        }
    }

    public void fill(int worldX, int worldZ, double[] target, ToDoubleBiFunction<Integer, Integer> resolver) {
        for (int row = 0; row < 16; row++) {
            int rowOffset = row << 4;
            int sampleZ = worldZ + row;
            for (int column = 0; column < 16; column++) {
                int key = rowOffset + column;
                target[key] = resolved(key, worldX + column, sampleZ, resolver);
            }
        }
    }

    private double resolved(int key, int x, int z, ToDoubleBiFunction<Integer, Integer> resolver) {
        if (((byte) STATE_HANDLE.getAcquire(states, key)) == STATE_READY) {
            return (double) VALUE_HANDLE.getVolatile(values, key);
        }
        return compute(key, x, z, resolver);
    }

    private double compute(int key, int x, int z, ToDoubleBiFunction<Integer, Integer> resolver) {
        byte state = (byte) STATE_HANDLE.getAcquire(states, key);
        if (state == STATE_READY) {
            return (double) VALUE_HANDLE.getVolatile(values, key);
        }
        if (state == STATE_EMPTY && STATE_HANDLE.compareAndSet(states, key, STATE_EMPTY, STATE_COMPUTING)) {
            double value;
            try {
                value = resolver.applyAsDouble(x, z);
            } catch (Throwable failure) {
                STATE_HANDLE.setRelease(states, key, STATE_EMPTY);
                throw failure;
            }
            VALUE_HANDLE.setRelease(values, key, value);
            STATE_HANDLE.setRelease(states, key, STATE_READY);
            return value;
        }
        // Another thread owns the slot. The resolver is pure, so computing the column here as
        // well costs one duplicate sample and never a wait; the owner publishes the shared copy.
        return resolver.applyAsDouble(x, z);
    }
}
