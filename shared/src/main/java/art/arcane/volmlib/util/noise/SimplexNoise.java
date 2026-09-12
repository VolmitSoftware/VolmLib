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

import art.arcane.volmlib.util.math.RNG;

public class SimplexNoise implements NoiseGenerator, OctaveNoise {
    private final FastNoiseDouble n;
    private int octaves;
    private double octaveBounding = 1D;

    public SimplexNoise(long seed) {
        this.n = new FastNoiseDouble(new RNG(seed).lmax());
        octaves = 1;
    }

    public double f(double v) {
        return (v / 2D) + 0.5D;
    }

    @Override
    public double noise(double x) {
        return f(noiseSigned(x));
    }

    @Override
    public double noiseSigned(double x) {
        if (octaves == 1) {
            return n.GetSimplex(x, 0D);
        }

        double frequency = 1D;
        double amplitude = 1D;
        double value = 0D;
        for (int i = 0; i < octaves; i++) {
            value += n.GetSimplex(x * frequency, 0D) * amplitude;
            frequency *= 2D;
            amplitude *= 0.5D;
        }
        return value * octaveBounding;
    }

    @Override
    public double noise(double x, double z) {
        return f(noiseSigned(x, z));
    }

    @Override
    public double noiseSigned(double x, double z) {
        if (octaves == 1) {
            return n.GetSimplex(x, z);
        }

        double frequency = 1D;
        double amplitude = 1D;
        double value = 0D;
        for (int i = 0; i < octaves; i++) {
            value += n.GetSimplex(x * frequency, z * frequency) * amplitude;
            frequency *= 2D;
            amplitude *= 0.5D;
        }
        return value * octaveBounding;
    }

    @Override
    public double noise(double x, double y, double z) {
        return f(noiseSigned(x, y, z));
    }

    @Override
    public double noiseSigned(double x, double y, double z) {
        if (octaves == 1) {
            return n.GetSimplex(x, y, z);
        }

        double frequency = 1D;
        double amplitude = 1D;
        double value = 0D;
        for (int i = 0; i < octaves; i++) {
            value += n.GetSimplex(x * frequency, y * frequency, z * frequency) * amplitude;
            frequency *= 2D;
            amplitude *= 0.5D;
        }
        return value * octaveBounding;
    }

    @Override
    public void setOctaves(int octaves) {
        this.octaves = Math.max(1, Math.min(16, octaves));
        octaveBounding = 1D / (2D - Math.scalb(1D, 1 - this.octaves));
    }

}
