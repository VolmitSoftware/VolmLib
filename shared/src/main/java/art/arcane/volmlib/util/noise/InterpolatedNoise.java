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

package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;

import java.util.Arrays;

/**
 * Interpolates a source noise over a 32 block lattice. Neighbouring queries share lattice points, so each thread
 * keeps a small memo of the source samples it has taken; the source is pure, so a memoized sample is the value a
 * fresh call would return. Changing the octaves retires every memo.
 */
public class InterpolatedNoise implements NoiseGenerator, OctaveNoise {
    private static final int MEMO_SLOTS = 64;

    private final InterpolationMethod method;
    private final NoiseGenerator generator;
    private final double coordinateScale;
    private final ThreadLocal<LatticeMemo> memo = ThreadLocal.withInitial(LatticeMemo::new);
    private volatile int sourceVersion;

    public InterpolatedNoise(long seed, NoiseType type, InterpolationMethod method) {
        this.method = method;
        generator = type.create(seed);
        coordinateScale = type.getCoordinateScale();
    }

    @Override
    public double noise(double x) {
        return noise(x, 0);
    }

    @Override
    public double noise(double x, double z) {
        LatticeMemo samples = memo.get();
        samples.retireIfStale(sourceVersion);
        return Math.max(0D, Math.min(1D, IrisInterpolation.getNoise(method, x, z, 32, samples)));
    }

    @Override
    public double noise(double x, double y, double z) {
        return noise(x, z);
    }

    @Override
    public void setOctaves(int octaves) {
        if (generator instanceof OctaveNoise octaveNoise) {
            octaveNoise.setOctaves(octaves);
            sourceVersion++;
        }
    }

    private final class LatticeMemo implements NoiseProvider {
        private final long[] xBits = new long[MEMO_SLOTS];
        private final long[] zBits = new long[MEMO_SLOTS];
        private final double[] values = new double[MEMO_SLOTS];
        private final boolean[] filled = new boolean[MEMO_SLOTS];
        private int version;

        private void retireIfStale(int current) {
            if (version != current) {
                Arrays.fill(filled, false);
                version = current;
            }
        }

        @Override
        public double noise(double x, double z) {
            long xKey = Double.doubleToRawLongBits(x);
            long zKey = Double.doubleToRawLongBits(z);
            int slot = (int) ((xKey * 0x9E3779B97F4A7C15L ^ zKey * 0xC2B2AE3D27D4EB4FL) >>> 58);
            if (filled[slot] && xBits[slot] == xKey && zBits[slot] == zKey) {
                return values[slot];
            }
            double value = generator.noise(x * coordinateScale, z * coordinateScale);
            xBits[slot] = xKey;
            zBits[slot] = zKey;
            values[slot] = value;
            filled[slot] = true;
            return value;
        }
    }
}
