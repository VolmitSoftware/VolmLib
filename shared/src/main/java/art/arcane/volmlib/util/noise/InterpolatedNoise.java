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
public class InterpolatedNoise implements NoiseGenerator, OctaveNoise {
    private final InterpolationMethod method;
    private final NoiseGenerator generator;
    private final NoiseProvider p;

    public InterpolatedNoise(long seed, NoiseType type, InterpolationMethod method) {
        this.method = method;
        generator = type.create(seed);
        double coordinateScale = type.getCoordinateScale();
        p = (x, z) -> generator.noise(x * coordinateScale, z * coordinateScale);
    }

    @Override
    public double noise(double x) {
        return noise(x, 0);
    }

    @Override
    public double noise(double x, double z) {
        return Math.max(0D, Math.min(1D, IrisInterpolation.getNoise(method, x, z, 32, p)));
    }

    @Override
    public double noise(double x, double y, double z) {
        return noise(x, z);
    }

    @Override
    public void setOctaves(int octaves) {
        if (generator instanceof OctaveNoise octaveNoise) {
            octaveNoise.setOctaves(octaves);
        }
    }
}
