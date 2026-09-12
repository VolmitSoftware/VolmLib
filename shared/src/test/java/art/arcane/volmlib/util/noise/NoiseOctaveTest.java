package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class NoiseOctaveTest {
    private static final long SEED = 972453L;
    private static final double X = 37.219D;
    private static final double Y = -91.713D;
    private static final double Z = 152.875D;

    @Test
    public void simplexAndPerlinKeepFundamentalAndReduceDetailAmplitude() {
        for (boolean simplex : new boolean[]{false, true}) {
            NoiseGenerator noise = simplex ? new SimplexNoise(SEED) : new PerlinNoise(SEED);
            ((OctaveNoise) noise).setOctaves(3);
            FastNoiseDouble source = new FastNoiseDouble(new RNG(SEED).lmax());
            source.setLongerp(FastNoiseDouble.Longerp.Qulongic);
            for (int dimensions = 1; dimensions <= 3; dimensions++) {
                double expected = 0D;
                for (int octave = 0; octave < 3; octave++) {
                    double frequency = 1 << octave;
                    double sample;
                    if (dimensions == 3) {
                        sample = simplex ? source.GetSimplex(X * frequency, Y * frequency, Z * frequency)
                                : source.GetPerlin(X * frequency, Y * frequency, Z * frequency);
                    } else {
                        double second = dimensions == 1 ? 0D : Z * frequency;
                        sample = simplex ? source.GetSimplex(X * frequency, second)
                                : source.GetPerlin(X * frequency, second);
                    }
                    expected += sample / frequency;
                }
                expected /= 1.75D;
                double signed = switch (dimensions) {
                    case 1 -> noise.noiseSigned(X);
                    case 2 -> noise.noiseSigned(X, Z);
                    default -> noise.noiseSigned(X, Y, Z);
                };
                double unsigned = switch (dimensions) {
                    case 1 -> noise.noise(X);
                    case 2 -> noise.noise(X, Z);
                    default -> noise.noise(X, Y, Z);
                };
                assertEquals(expected, signed, 1E-15D);
                assertEquals(signed / 2D + 0.5D, unsigned, 0D);
            }
            assertEquals(noise.noise(X, 0D), noise.noise(X), 0D);
        }
    }

    @Test(timeout = 5000)
    public void octaveWrappersClampInvalidAndExcessiveCounts() {
        NoiseGenerator[] generators = {
                new SimplexNoise(SEED), new PerlinNoise(SEED), new FractalFBMSimplexNoise(SEED),
                new FractalBillowSimplexNoise(SEED), new FractalBillowPerlinNoise(SEED),
                new FractalRigidMultiSimplexNoise(SEED), new FractalCubicNoise(SEED)
        };
        for (NoiseGenerator noise : generators) {
            OctaveNoise octaveNoise = (OctaveNoise) noise;
            octaveNoise.setOctaves(1);
            double minimum = noise.noise(X, Y, Z);
            octaveNoise.setOctaves(Integer.MIN_VALUE);
            assertEquals(minimum, noise.noise(X, Y, Z), 0D);
            octaveNoise.setOctaves(16);
            double maximum = noise.noise(X, Y, Z);
            octaveNoise.setOctaves(Integer.MAX_VALUE);
            assertEquals(maximum, noise.noise(X, Y, Z), 0D);
            assertTrue(Double.isFinite(maximum));
        }
    }

    @Test
    public void cubicFractalUsesRequestedOctaves() {
        FractalCubicNoise noise = new FractalCubicNoise(SEED);
        noise.setOctaves(1);
        double fundamental = noise.noise(X, Y, Z);
        noise.setOctaves(4);
        double detailed = noise.noise(X, Y, Z);
        assertNotEquals(fundamental, detailed, 1E-8D);
        FastNoiseDouble source = new FastNoiseDouble(new RNG(SEED).lmax());
        source.setFractalType(FastNoiseDouble.FractalType.Billow);
        source.setFractalOctaves(4);
        assertEquals(source.GetCubicFractal(X, Y, Z) / 2D + 0.5D, detailed, 0D);
    }

    @Test(timeout = 5000)
    public void rawFractalSamplersBoundOctaveWork() {
        FastNoiseDouble doubles = new FastNoiseDouble(SEED);
        FastNoise floats = new FastNoise((int) SEED);
        doubles.setFractalOctaves(16);
        floats.SetFractalOctaves(16);
        double expectedDouble = doubles.GetSimplexFractal(X, Y, Z);
        float expectedFloat = floats.GetSimplexFractal((float) X, (float) Y, (float) Z);
        doubles.setFractalOctaves(Long.MAX_VALUE);
        floats.SetFractalOctaves(Integer.MAX_VALUE);
        assertEquals(expectedDouble, doubles.GetSimplexFractal(X, Y, Z), 0D);
        assertEquals(expectedFloat, floats.GetSimplexFractal((float) X, (float) Y, (float) Z), 0D);
    }
}
