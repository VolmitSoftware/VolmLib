package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ClassicNoiseQualityTest {
    private static final long SEED = 972453L;

    @Test
    public void directCubicUsesConfiguredSeedInBothPrecisions() {
        for (long seed : new long[]{1L, -23L, Long.MAX_VALUE}) {
            FastNoiseDouble doubles = new FastNoiseDouble(seed);
            doubles.setNoiseType(FastNoiseDouble.NoiseType.Cubic);
            FastNoise floats = new FastNoise((int) seed);
            floats.SetNoiseType(FastNoise.NoiseType.Cubic);
            for (int x = -300; x <= 300; x += 37) {
                double sampleX = x + 0.193D;
                double sampleZ = x * 0.71D + 43.517D;
                assertEquals(doubles.GetNoise(sampleX, sampleZ), doubles.GetCubic(sampleX, sampleZ), 0D);
                assertEquals(floats.GetNoise((float) sampleX, (float) sampleZ),
                        floats.GetCubic((float) sampleX, (float) sampleZ), 0F);
            }
        }
        CubicNoise first = new CubicNoise(SEED);
        CubicNoise second = new CubicNoise(SEED + 1);
        assertNotEquals(first.noise(37.219D), second.noise(37.219D), 1E-8D);
        assertNotEquals(first.noise(37.219D, -91.713D), second.noise(37.219D, -91.713D), 1E-8D);
    }

    @Test
    public void perlinDefaultsToQuinticSmoothing() {
        FastNoiseDouble source = new FastNoiseDouble(new RNG(SEED).lmax());
        source.setLongerp(FastNoiseDouble.Longerp.Qulongic);
        source.setFractalType(FastNoiseDouble.FractalType.Billow);
        source.setFractalOctaves(1);
        PerlinNoise perlin = new PerlinNoise(SEED);
        FractalBillowPerlinNoise billow = new FractalBillowPerlinNoise(SEED);
        for (int x = -240; x <= 240; x += 17) {
            double sampleX = x + 0.37D;
            double sampleZ = x * 0.91D - 8.143D;
            assertEquals(source.GetPerlin(sampleX, sampleZ), perlin.noiseSigned(sampleX, sampleZ), 0D);
            assertEquals(source.GetPerlinFractal(sampleX, sampleZ) / 2D + 0.5D,
                    billow.noise(sampleX, sampleZ), 0D);
            assertEquals(source.GetPerlin(sampleX, 63.21D, sampleZ),
                    perlin.noiseSigned(sampleX, 63.21D, sampleZ), 0D);
            assertEquals(source.GetPerlinFractal(sampleX, 63.21D, sampleZ) / 2D + 0.5D,
                    billow.noise(sampleX, 63.21D, sampleZ), 0D);
        }
    }

    @Test
    public void perlinSlopesRemainContinuousAcrossLatticeBoundaries() {
        PerlinNoise noise = new PerlinNoise(SEED);
        double step = 0.001D;
        for (int x = -400; x <= 400; x += 100) {
            double center = noise.noiseSigned(x, 37.219D, -91.713D);
            double leftSlope = (center - noise.noiseSigned(x - step, 37.219D, -91.713D)) / step;
            double rightSlope = (noise.noiseSigned(x + step, 37.219D, -91.713D) - center) / step;
            assertEquals(leftSlope, rightSlope, 1E-8D);
        }
    }

    @Test
    public void ridgedSimplexNormalizesTheActualOctaveRange() {
        FractalRigidMultiSimplexNoise ridge = new FractalRigidMultiSimplexNoise(SEED);
        FastNoiseDouble source = new FastNoiseDouble(new RNG(SEED).lmax());
        source.setFractalType(FastNoiseDouble.FractalType.RigidMulti);
        for (int octaves : new int[]{1, 2, 4, 8, 16}) {
            ridge.setOctaves(octaves);
            source.setFractalOctaves(octaves);
            double amplitudeSum = 2D - Math.scalb(1D, 1 - octaves);
            assertEquals(0D, ridge.f(1D - amplitudeSum), 1E-15D);
            assertEquals(1D, ridge.f(1D), 0D);
            for (int x = -400; x <= 400; x += 11) {
                double sampleX = x + 0.37D;
                double sampleZ = x * 0.91D - 8.143D;
                double value = ridge.noise(sampleX, sampleZ);
                double raw = source.GetSimplexFractal(sampleX, sampleZ);
                assertEquals((raw + amplitudeSum - 1D) / amplitudeSum, value, 1E-15D);
                assertTrue(value >= 0D && value <= 1D);
                assertEquals(value * 2D - 1D, ridge.noiseSigned(sampleX, sampleZ), 0D);
            }
        }
        ridge.setOctaves(1);
        assertEquals(1D - Math.abs(source.GetSimplex(37.219D, -91.713D)),
                ridge.noise(37.219D, -91.713D), 0D);
    }

    @Test
    public void classicAndCellularFamiliesRemainBoundedAtWorldEdges() {
        NoiseGenerator[] generators = {
                new CubicNoise(SEED), new FractalCubicNoise(SEED), new FractalBillowPerlinNoise(SEED),
                new FractalBillowSimplexNoise(SEED), new FractalFBMSimplexNoise(SEED),
                new FractalRigidMultiSimplexNoise(SEED), new GlobNoise(SEED), new CellHeightNoise(SEED),
                new VascularNoise(SEED), new CellularNoise(SEED)
        };
        for (NoiseGenerator generator : generators) {
            for (int octave : new int[]{1, 4, 16}) {
                if (generator instanceof OctaveNoise fractal) {
                    fractal.setOctaves(octave);
                }
                for (double origin : new double[]{-30_000_000D, 0D, 30_000_000D}) {
                    for (int i = 0; i < 64; i++) {
                        double x = origin + i * 0.317D;
                        double z = -origin - i * 0.913D;
                        assertBounded(generator.noise(x));
                        assertBounded(generator.noise(x, z));
                        assertBounded(generator.noise(x, 71.917D, z));
                    }
                }
            }
        }
    }

    @Test
    public void cellularHeightAndVascularKeepComplementaryProfiles() {
        CellHeightNoise height = new CellHeightNoise(SEED);
        VascularNoise vascular = new VascularNoise(SEED);
        for (int x = -300; x <= 300; x += 13) {
            double sampleX = x + 0.91D;
            double sampleZ = x * 0.31D - 7.193D;
            assertEquals(1D, height.noise(sampleX, sampleZ) + vascular.noise(sampleX, sampleZ), 1E-15D);
            assertEquals(1D, height.noise(sampleX, 87.137D, sampleZ)
                    + vascular.noise(sampleX, 87.137D, sampleZ), 1E-15D);
        }
    }

    @Test
    public void vascularBrightensWhereCellDistancesConverge() {
        FastNoiseDouble distance = new FastNoiseDouble(new RNG(SEED).lmax());
        distance.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
        distance.setCellularReturnType(FastNoiseDouble.CellularReturnType.Distance2Sub);
        CellHeightNoise height = new CellHeightNoise(SEED);
        VascularNoise vascular = new VascularNoise(SEED);
        int borderSamples = 0;
        int interiorSamples = 0;
        for (int x = -500; x <= 500; x++) {
            double sampleX = x + 0.137D;
            double sampleZ = x * 3.17D - 71.913D;
            double separation = distance.GetCellular(sampleX, sampleZ) + 1D;
            if (separation < 0.02D) {
                assertTrue(vascular.noise(sampleX, sampleZ) > 0.99D);
                assertTrue(height.noise(sampleX, sampleZ) < 0.01D);
                borderSamples++;
            } else if (separation > 1D) {
                assertTrue(vascular.noise(sampleX, sampleZ) < 0.5D);
                assertTrue(height.noise(sampleX, sampleZ) > 0.5D);
                interiorSamples++;
            }
            assertEquals(vascular.noise(sampleX, sampleZ) * 2D - 1D,
                    vascular.noiseSigned(sampleX, sampleZ), 2E-16D);
        }
        assertTrue(borderSamples > 0);
        assertTrue(interiorSamples > 0);
    }

    @Test
    public void fractalConstructorsStartAtOneOctave() {
        NoiseGenerator[] generators = {
                new FractalCubicNoise(SEED), new FractalFBMSimplexNoise(SEED),
                new FractalBillowPerlinNoise(SEED), new FractalBillowSimplexNoise(SEED),
                new FractalRigidMultiSimplexNoise(SEED)
        };
        for (NoiseGenerator generator : generators) {
            double initial = generator.noise(37.219D, -91.713D, 17.731D);
            ((OctaveNoise) generator).setOctaves(1);
            assertEquals(initial, generator.noise(37.219D, -91.713D, 17.731D), 0D);
        }
    }

    private void assertBounded(double value) {
        assertTrue(Double.isFinite(value) && value >= 0D && value <= 1D);
    }
}
