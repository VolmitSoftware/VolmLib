package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.interpolation.InterpolationMethod;

public enum NoiseType {
    WHITE(WhiteNoise::new),
    WHITE_BILINEAR((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.BILINEAR)),
    WHITE_BICUBIC((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.BICUBIC)),
    WHITE_HERMITE((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.HERMITE)),
    SIMPLEX(SimplexNoise::new),
    PERLIN(seed -> new PerlinNoise(seed).hermite()),
    FRACTAL_BILLOW_SIMPLEX(FractalBillowSimplexNoise::new),
    FRACTAL_BILLOW_PERLIN(FractalBillowPerlinNoise::new),
    FRACTAL_FBM_SIMPLEX(FractalFBMSimplexNoise::new),
    FRACTAL_RIGID_MULTI_SIMPLEX(FractalRigidMultiSimplexNoise::new),
    FLAT(FlatNoise::new),
    CELLULAR(CellularNoise::new),
    CELLULAR_BILINEAR((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR)),
    CELLULAR_BICUBIC((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BICUBIC)),
    CELLULAR_HERMITE((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE)),
    GLOB(GlobNoise::new),
    CUBIC(CubicNoise::new),
    FRACTAL_CUBIC(FractalCubicNoise::new),
    CELLULAR_HEIGHT(CellHeightNoise::new),
    CLOVER(CloverNoise::new),
    CLOVER_BILINEAR((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR)),
    CLOVER_BICUBIC((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BICUBIC)),
    CLOVER_HERMITE((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE)),
    HEXAGON(HexagonNoise::new),
    HEXAGON_BILINEAR((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.BILINEAR)),
    HEXAGON_BICUBIC((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.BICUBIC)),
    HEXAGON_HERMITE((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.HERMITE)),
    HEX_JAMES(HexJamesNoise::new),
    HEX_JAMES_BILINEAR((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.BILINEAR)),
    HEX_JAMES_BICUBIC((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.BICUBIC)),
    HEX_JAMES_HERMITE((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.HERMITE)),
    HEX_SIMPLEX(HexSimplexNoise::new),
    HEX_SIMPLEX_BILINEAR((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.BILINEAR)),
    HEX_SIMPLEX_BICUBIC((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.BICUBIC)),
    HEX_SIMPLEX_HERMITE((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.HERMITE)),
    HEX_RANDOM_SIZE(HexRandomSizeNoise::new),
    HEX_RANDOM_SIZE_BILINEAR((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.BILINEAR)),
    HEX_RANDOM_SIZE_BICUBIC((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.BICUBIC)),
    HEX_RANDOM_SIZE_HERMITE((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.HERMITE)),
    SIERPINSKI_TRIANGLE(SierpinskiTriangleNoise::new),
    SIERPINSKI_TRIANGLE_BILINEAR((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.BILINEAR)),
    SIERPINSKI_TRIANGLE_BICUBIC((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.BICUBIC)),
    SIERPINSKI_TRIANGLE_HERMITE((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.HERMITE)),
    VASCULAR(VascularNoise::new);

    private final NoiseFactory f;

    NoiseType(NoiseFactory f) {
        this.f = f;
    }

    public NoiseGenerator create(long seed) {
        return f.create(seed);
    }
}
