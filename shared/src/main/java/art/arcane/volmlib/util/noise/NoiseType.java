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

import art.arcane.volmlib.util.interpolation.InterpolationMethod;
public enum NoiseType {
    WHITE(WhiteNoise::new, 1D),
    WHITE_BILINEAR((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.BILINEAR), 1D),
    WHITE_BICUBIC((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.BICUBIC), 1D),
    WHITE_HERMITE((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.HERMITE), 1D),
    SIMPLEX(SimplexNoise::new),
    PERLIN(PerlinNoise::new),
    FRACTAL_BILLOW_SIMPLEX(FractalBillowSimplexNoise::new),
    FRACTAL_BILLOW_PERLIN(FractalBillowPerlinNoise::new),
    FRACTAL_FBM_SIMPLEX(FractalFBMSimplexNoise::new),
    FRACTAL_RIGID_MULTI_SIMPLEX(FractalRigidMultiSimplexNoise::new),
    FLAT(FlatNoise::new, 1D),
    CELLULAR(CellularNoise::new),
    CELLULAR_BILINEAR((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR), 1D),
    CELLULAR_BILINEAR_STARCAST_3((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR_STARCAST_3), 1D),
    CELLULAR_BILINEAR_STARCAST_6((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR_STARCAST_6), 1D),
    CELLULAR_BILINEAR_STARCAST_9((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR_STARCAST_9), 1D),
    CELLULAR_BILINEAR_STARCAST_12((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR_STARCAST_12), 1D),
    CELLULAR_BICUBIC((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BICUBIC), 1D),
    CELLULAR_HERMITE((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE), 1D),
    CELLULAR_STARCAST_3((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.STARCAST_3), 1D),
    CELLULAR_STARCAST_6((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.STARCAST_6), 1D),
    CELLULAR_STARCAST_9((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.STARCAST_9), 1D),
    CELLULAR_STARCAST_12((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.STARCAST_12), 1D),
    CELLULAR_HERMITE_STARCAST_3((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE_STARCAST_3), 1D),
    CELLULAR_HERMITE_STARCAST_6((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE_STARCAST_6), 1D),
    CELLULAR_HERMITE_STARCAST_9((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE_STARCAST_9), 1D),
    CELLULAR_HERMITE_STARCAST_12((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE_STARCAST_12), 1D),
    GLOB(GlobNoise::new),
    CUBIC(CubicNoise::new),
    FRACTAL_CUBIC(FractalCubicNoise::new),
    CELLULAR_HEIGHT(CellHeightNoise::new),
    CLOVER(CloverNoise::new, 1D / 64D),
    CLOVER_BILINEAR((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR), 1D),
    CLOVER_BILINEAR_STARCAST_3((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR_STARCAST_3), 1D),
    CLOVER_BILINEAR_STARCAST_6((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR_STARCAST_6), 1D),
    CLOVER_BILINEAR_STARCAST_9((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR_STARCAST_9), 1D),
    CLOVER_BILINEAR_STARCAST_12((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR_STARCAST_12), 1D),
    CLOVER_BICUBIC((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BICUBIC), 1D),
    CLOVER_HERMITE((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE), 1D),
    CLOVER_STARCAST_3((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.STARCAST_3), 1D),
    CLOVER_STARCAST_6((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.STARCAST_6), 1D),
    CLOVER_STARCAST_9((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.STARCAST_9), 1D),
    CLOVER_STARCAST_12((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.STARCAST_12), 1D),
    CLOVER_HERMITE_STARCAST_3((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE_STARCAST_3), 1D),
    CLOVER_HERMITE_STARCAST_6((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE_STARCAST_6), 1D),
    CLOVER_HERMITE_STARCAST_9((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE_STARCAST_9), 1D),
    CLOVER_HERMITE_STARCAST_12((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE_STARCAST_12), 1D),
    HEXAGON(HexagonNoise::new, 2D / 64D),
    HEXAGON_BILINEAR((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.BILINEAR), 1D),
    HEXAGON_BICUBIC((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.BICUBIC), 1D),
    HEXAGON_HERMITE((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.HERMITE), 1D),
    HEX_JAMES(HexJamesNoise::new, 2D / 64D),
    HEX_JAMES_BILINEAR((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.BILINEAR), 1D),
    HEX_JAMES_BICUBIC((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.BICUBIC), 1D),
    HEX_JAMES_HERMITE((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.HERMITE), 1D),
    HEX_SIMPLEX(HexSimplexNoise::new, 2D / 64D),
    HEX_SIMPLEX_BILINEAR((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.BILINEAR), 1D),
    HEX_SIMPLEX_BICUBIC((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.BICUBIC), 1D),
    HEX_SIMPLEX_HERMITE((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.HERMITE), 1D),
    HEX_RANDOM_SIZE(HexRandomSizeNoise::new, 2D / 64D),
    HEX_RANDOM_SIZE_BILINEAR((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.BILINEAR), 1D),
    HEX_RANDOM_SIZE_BICUBIC((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.BICUBIC), 1D),
    HEX_RANDOM_SIZE_HERMITE((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.HERMITE), 1D),
    SIERPINSKI_TRIANGLE(SierpinskiTriangleNoise::new, 8D / 64D),
    SIERPINSKI_TRIANGLE_BILINEAR((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.BILINEAR), 1D),
    SIERPINSKI_TRIANGLE_BICUBIC((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.BICUBIC), 1D),
    SIERPINSKI_TRIANGLE_HERMITE((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.HERMITE), 1D),
    VASCULAR(VascularNoise::new),
    GYROID(GyroidNoise::new, 1D / 64D),
    QUASICRYSTAL(QuasicrystalNoise::new, 1D / 64D),
    TRUCHET(TruchetNoise::new, 1D / 64D),
    CRATER(CraterNoise::new, 1D / 64D),
    VORTEX(VortexNoise::new, 1D / 64D),
    DUNE(DuneNoise::new, 1D / 64D),
    STRATA(StrataNoise::new, 1D / 64D),
    WOOD(WoodNoise::new, 1D / 64D),
    GABOR(GaborNoise::new, 1D / 64D),
    MARBLE(MarbleNoise::new, 1D / 64D),
    SCALES(ScalesNoise::new, 1D / 64D),
    CHLADNI(ChladniNoise::new, 1D / 64D),
    KALEIDOSCOPE(KaleidoscopeNoise::new, 1D / 64D),
    MENGER_SPONGE(MengerSpongeNoise::new, 1D / 64D),
    CIRCUIT(CircuitNoise::new, 1D / 64D);

    private final NoiseFactory f;
    private final double coordinateScale;

    NoiseType(NoiseFactory f) {
        this(f, 100D / 64D);
    }

    NoiseType(NoiseFactory f, double coordinateScale) {
        this.f = f;
        this.coordinateScale = coordinateScale;
    }

    public double getCoordinateScale() {
        return coordinateScale;
    }

    public NoiseGenerator create(long seed) {
        return f.create(seed).offset(seed);
    }
}
