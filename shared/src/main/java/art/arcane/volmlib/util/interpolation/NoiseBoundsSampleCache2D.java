/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.util.interpolation;

import art.arcane.volmlib.util.function.NoiseProvider;

import java.util.Arrays;

/**
 * Open-addressed memo table that samples a {@link NoiseBoundsProvider} once per column and serves
 * the minimum while recording maxima for an identical second interpolation pass. Single threaded; held in
 * thread locals.
 * <p>
 * The minimum view samples the bound provider; the maximum view replays the same sampling sequence.
 * Both views are allocated once per cache instance.
 */
final class NoiseBoundsSampleCache2D {
    private final NoiseProvider minView = this::sampleMin;
    private final NoiseProvider replayMaxView = this::replayMax;
    private NoiseBoundsProvider boundProvider;
    private long[] xBits;
    private long[] zBits;
    private double[] minValues;
    private double[] maxValues;
    private byte[] states;
    private int mask;
    private int resizeThreshold;
    private int size;
    private boolean inUse;
    private double[] sampledMaximums = new double[64];
    private int sampledMaximumCount;
    private int maximumReplayIndex;

    public NoiseBoundsSampleCache2D(int initialCapacity) {
        int minimumCapacity = Math.max(8, initialCapacity);
        int tableSize = tableSizeFor((minimumCapacity << 1) + minimumCapacity);
        xBits = new long[tableSize];
        zBits = new long[tableSize];
        minValues = new double[tableSize];
        maxValues = new double[tableSize];
        states = new byte[tableSize];
        mask = tableSize - 1;
        resizeThreshold = Math.max(1, (tableSize * 3) >> 2);
        size = 0;
    }

    public void clear() {
        if (size == 0) {
            return;
        }
        Arrays.fill(states, (byte) 0);
        size = 0;
    }

    /**
     * Bounds interpolation nests on one thread; a nested pass must never reuse the outer
     * pass's table, since entries belong to one specific bound provider.
     */
    public boolean isInUse() {
        return inUse;
    }

    public void beginUse() {
        inUse = true;
        sampledMaximumCount = 0;
        maximumReplayIndex = 0;
        clear();
    }

    public void endUse() {
        inUse = false;
    }

    /**
     * Binds the provider that {@link #minView()} samples, returning the
     * previously bound provider so callers can restore it.
     */
    public NoiseBoundsProvider bindProvider(NoiseBoundsProvider provider) {
        NoiseBoundsProvider previous = boundProvider;
        boundProvider = provider;
        return previous;
    }

    public NoiseProvider minView() {
        return minView;
    }

    public NoiseProvider replayMaxView() {
        return replayMaxView;
    }

    private double sampleMin(double sampleX, double sampleZ) {
        long sampleXBits = Double.doubleToLongBits(sampleX);
        long sampleZBits = Double.doubleToLongBits(sampleZ);
        int slot = findSlot(sampleXBits, sampleZBits);
        double minimum;
        double maximum;
        if (states[slot] != 0) {
            minimum = minValues[slot];
            maximum = maxValues[slot];
        } else {
            NoiseBounds bounds = boundProvider.noise(sampleX, sampleZ);
            minimum = bounds.min();
            maximum = bounds.max();
            insert(findSlot(sampleXBits, sampleZBits), sampleXBits, sampleZBits, minimum, maximum);
        }
        if (sampledMaximumCount == sampledMaximums.length) {
            sampledMaximums = Arrays.copyOf(sampledMaximums, sampledMaximums.length << 1);
        }
        sampledMaximums[sampledMaximumCount++] = maximum;
        return minimum;
    }

    private double replayMax(double sampleX, double sampleZ) {
        return sampledMaximums[maximumReplayIndex++];
    }

    private int findSlot(long xb, long zb) {
        int slot = mix(xb, zb) & mask;
        while (states[slot] != 0) {
            if (xBits[slot] == xb && zBits[slot] == zb) {
                break;
            }
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private void insert(int slot, long xb, long zb, double min, double max) {
        xBits[slot] = xb;
        zBits[slot] = zb;
        minValues[slot] = min;
        maxValues[slot] = max;
        states[slot] = 1;
        size++;
        if (size >= resizeThreshold) {
            grow();
        }
    }

    private int mix(long xb, long zb) {
        long hash = xb * 0x9E3779B97F4A7C15L;
        hash ^= Long.rotateLeft(zb * 0xC2B2AE3D27D4EB4FL, 32);
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        return (int) hash;
    }

    private void grow() {
        long[] previousXBits = xBits;
        long[] previousZBits = zBits;
        double[] previousMin = minValues;
        double[] previousMax = maxValues;
        byte[] previousStates = states;

        int nextLength = xBits.length << 1;
        long[] nextXBits = new long[nextLength];
        long[] nextZBits = new long[nextLength];
        double[] nextMin = new double[nextLength];
        double[] nextMax = new double[nextLength];
        byte[] nextStates = new byte[nextLength];

        xBits = nextXBits;
        zBits = nextZBits;
        minValues = nextMin;
        maxValues = nextMax;
        states = nextStates;
        mask = nextLength - 1;
        resizeThreshold = Math.max(1, (nextLength * 3) >> 2);
        size = 0;

        for (int i = 0; i < previousStates.length; i++) {
            if (previousStates[i] == 0) {
                continue;
            }
            int slot = findSlot(previousXBits[i], previousZBits[i]);
            xBits[slot] = previousXBits[i];
            zBits[slot] = previousZBits[i];
            minValues[slot] = previousMin[i];
            maxValues[slot] = previousMax[i];
            states[slot] = 1;
            size++;
        }
    }

    private int tableSizeFor(int value) {
        int n = value - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        int tableSize = n + 1;
        if (tableSize < 8) {
            return 8;
        }
        return tableSize;
    }
}
