package art.arcane.volmlib.util.cache;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.ToDoubleBiFunction;

public class ChunkCache2DDouble {
    private static final VarHandle STATE_HANDLE = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final VarHandle VALUE_HANDLE = MethodHandles.arrayElementVarHandle(double[].class);

    private final byte[] states = new byte[256];
    private final double[] values = new double[256];

    public double get(int x, int z, ToDoubleBiFunction<Integer, Integer> resolver) {
        int key = ((z & 15) << 4) | (x & 15);
        if (((byte) STATE_HANDLE.getAcquire(states, key)) != 0) {
            return (double) VALUE_HANDLE.getVolatile(values, key);
        }

        return compute(key, x, z, resolver);
    }

    public void fill(int worldX, int worldZ, Object[] target, ToDoubleBiFunction<Integer, Integer> resolver) {
        synchronized (this) {
            for (int row = 0; row < 16; row++) {
                int rowOffset = row << 4;
                int sampleZ = worldZ + row;
                for (int column = 0; column < 16; column++) {
                    int key = rowOffset + column;
                    if (states[key] == 0) {
                        values[key] = resolver.applyAsDouble(worldX + column, sampleZ);
                        states[key] = 1;
                    }

                    target[key] = values[key];
                }
            }
        }
    }

    public void fill(int worldX, int worldZ, double[] target, ToDoubleBiFunction<Integer, Integer> resolver) {
        synchronized (this) {
            for (int row = 0; row < 16; row++) {
                int rowOffset = row << 4;
                int sampleZ = worldZ + row;
                for (int column = 0; column < 16; column++) {
                    int key = rowOffset + column;
                    if (states[key] == 0) {
                        values[key] = resolver.applyAsDouble(worldX + column, sampleZ);
                        states[key] = 1;
                    }

                    target[key] = values[key];
                }
            }
        }
    }

    private double compute(int key, int x, int z, ToDoubleBiFunction<Integer, Integer> resolver) {
        synchronized (this) {
            if (((byte) STATE_HANDLE.getAcquire(states, key)) == 0) {
                double value = resolver.applyAsDouble(x, z);
                VALUE_HANDLE.setRelease(values, key, value);
                STATE_HANDLE.setRelease(states, key, (byte) 1);
            }

            return (double) VALUE_HANDLE.getVolatile(values, key);
        }
    }
}
