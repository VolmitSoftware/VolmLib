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
 * Open-addressed memo table keyed on a relative sample offset, used to collapse the duplicate
 * noise probes that starcast composites issue for the same column. Single threaded by contract;
 * instances are held in thread locals.
 */
final class NoiseSampleCache2D {
    private long[] xBits;
    private long[] zBits;
    private double[] values;
    private byte[] states;
    private int mask;
    private int resizeThreshold;
    private int size;
    private boolean inUse;

    public NoiseSampleCache2D(int initialCapacity) {
        int minimumCapacity = Math.max(8, initialCapacity);
        int tableSize = tableSizeFor((minimumCapacity << 1) + minimumCapacity);
        xBits = new long[tableSize];
        zBits = new long[tableSize];
        values = new double[tableSize];
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
     * Interpolation nests on one thread (image maps and interpolated noise styles re-enter
     * {@code IrisInterpolation.getNoise} from inside a provider), and a nested pass sharing
     * this table would clear it mid-flight and cross-serve another provider's samples.
     */
    public boolean isInUse() {
        return inUse;
    }

    public void beginUse() {
        inUse = true;
        clear();
    }

    public void endUse() {
        inUse = false;
    }

    public double getOrSample(double relativeX, double relativeZ, double sampleX, double sampleZ, NoiseProvider provider) {
        long rx = Double.doubleToLongBits(relativeX);
        long rz = Double.doubleToLongBits(relativeZ);
        int slot = findSlot(rx, rz);
        if (states[slot] != 0) {
            return values[slot];
        }

        double value = provider.noise(sampleX, sampleZ);
        // Recompute the slot: the provider call may have mutated the table (clear/grow), so
        // the pre-call index can point into stale or reallocated arrays.
        insert(findSlot(rx, rz), rx, rz, value);
        return value;
    }

    private int findSlot(long rx, long rz) {
        int slot = mix(rx, rz) & mask;
        while (states[slot] != 0) {
            if (xBits[slot] == rx && zBits[slot] == rz) {
                break;
            }
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private void insert(int slot, long rx, long rz, double value) {
        xBits[slot] = rx;
        zBits[slot] = rz;
        values[slot] = value;
        states[slot] = 1;
        size++;
        if (size >= resizeThreshold) {
            grow();
        }
    }

    private int mix(long rx, long rz) {
        long hash = rx * 0x9E3779B97F4A7C15L;
        hash ^= Long.rotateLeft(rz * 0xC2B2AE3D27D4EB4FL, 32);
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        return (int) hash;
    }

    private void grow() {
        long[] previousXBits = xBits;
        long[] previousZBits = zBits;
        double[] previousValues = values;
        byte[] previousStates = states;

        int nextLength = xBits.length << 1;
        long[] nextXBits = new long[nextLength];
        long[] nextZBits = new long[nextLength];
        double[] nextValues = new double[nextLength];
        byte[] nextStates = new byte[nextLength];

        xBits = nextXBits;
        zBits = nextZBits;
        values = nextValues;
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
            values[slot] = previousValues[i];
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
        int size = n + 1;
        if (size < 8) {
            return 8;
        }
        return size;
    }
}
