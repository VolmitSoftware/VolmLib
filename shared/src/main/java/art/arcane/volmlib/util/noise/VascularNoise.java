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

public class VascularNoise implements NoiseGenerator {
    private final FastNoiseDouble n;

    public VascularNoise(long seed) {
        this.n = new FastNoiseDouble(new RNG(seed).lmax());
        n.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
        n.setCellularReturnType(FastNoiseDouble.CellularReturnType.Distance2Sub);
        n.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
    }

    private double filter(double noise) {
        double normalized = 0.5D - noise * 0.5D;
        if (normalized < 0D) {
            return 0D;
        }

        return normalized > 1D ? 1D : normalized;
    }

    private double filterSigned(double noise) {
        noise = -noise;
        if (noise < -1D) {
            return -1D;
        }

        return noise > 1D ? 1D : noise;
    }

    @Override
    public double noise(double x) {
        return filter(n.GetCellular(x, 0));
    }

    @Override
    public double noiseSigned(double x) {
        return filterSigned(n.GetCellular(x, 0D));
    }

    @Override
    public double noise(double x, double z) {
        return filter(n.GetCellular(x, z));
    }

    @Override
    public double noiseSigned(double x, double z) {
        return filterSigned(n.GetCellular(x, z));
    }

    @Override
    public double noise(double x, double y, double z) {
        return filter(n.GetCellular(x, y, z));
    }

    @Override
    public double noiseSigned(double x, double y, double z) {
        return filterSigned(n.GetCellular(x, y, z));
    }
}
