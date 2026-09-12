package art.arcane.volmlib.util.noise;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PatternNoiseTest {


    @Test
    public void patternsRemainBoundedAtEveryOctaveCountAndWorldEdge() {
        Random random = new Random(1337L);
        for (long seed : new long[]{0L, 1337L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (PatternNoise noise : patterns(seed)) {
                for (int octaves : new int[]{1, 2, 5, 16}) {
                    noise.setOctaves(octaves);
                    for (int sample = 0; sample < 128; sample++) {
                        double x = (random.nextDouble() * 2D - 1D) * 30_000_000D / 64D;
                        double y = (random.nextDouble() * 2D - 1D) * 2048D / 64D;
                        double z = (random.nextDouble() * 2D - 1D) * 30_000_000D / 64D;
                        assertUnit(noise, noise.noise(x));
                        assertUnit(noise, noise.noise(x, z));
                        assertUnit(noise, noise.noise(x, y, z));
                        assertEquals(noise.noise(x, z), noise.noise(x, 0D, z), 0D);
                        assertEquals(noise.noise(x), noise.noise(x, 0D), 0D);
                    }
                }
            }
        }
    }

    @Test
    public void allPatternsUseTheFullSeedAndVaryThroughHeight() {
        PatternNoise[] lowSeed = patterns(1337L);
        PatternNoise[] sameSeed = patterns(1337L);
        PatternNoise[] highSeed = patterns(1337L + (1L << 32));
        for (int index = 0; index < lowSeed.length; index++) {
            double seedDifference = 0D;
            double heightDifference = 0D;
            for (int sample = 0; sample < 128; sample++) {
                double x = sample * 0.173D - 8D;
                double z = sample * -0.193D + 7D;
                double value = lowSeed[index].noise(x, 0.371D, z);
                assertEquals(value, sameSeed[index].noise(x, 0.371D, z), 0D);
                seedDifference += Math.abs(value - highSeed[index].noise(x, 0.371D, z));
                heightDifference += Math.abs(value - lowSeed[index].noise(x, 0.891D, z));
            }
            assertTrue(lowSeed[index].getClass().getSimpleName(), seedDifference > 1D);
            assertTrue(lowSeed[index].getClass().getSimpleName(), heightDifference > 1D);
        }
    }

    @Test
    public void octavesAddDetailWithoutChangingTheBaseSeed() {
        for (PatternNoise noise : patterns(42L)) {
            double[] original = new double[128];
            for (int index = 0; index < original.length; index++) {
                original[index] = noise.noise(index * 0.073D, 0.419D, index * -0.113D);
            }
            noise.setOctaves(4);
            double difference = 0D;
            for (int index = 0; index < original.length; index++) {
                difference += Math.abs(original[index] - noise.noise(index * 0.073D, 0.419D, index * -0.113D));
            }
            assertTrue(noise.getClass().getSimpleName(), difference > 1D);
            noise.setOctaves(0);
            for (int index = 0; index < original.length; index++) {
                assertEquals(original[index], noise.noise(index * 0.073D, 0.419D, index * -0.113D), 0D);
            }
            noise.setOctaves(16);
            double maximum = noise.noise(0.713D, 0.931D, -0.719D);
            noise.setOctaves(Integer.MAX_VALUE);
            assertEquals(maximum, noise.noise(0.713D, 0.931D, -0.719D), 0D);
        }
    }


    private static PatternNoise[] patterns(long seed) {
        return new PatternNoise[]{new GyroidNoise(seed), new QuasicrystalNoise(seed),
                new TruchetNoise(seed), new CraterNoise(seed), new VortexNoise(seed),
                new DuneNoise(seed), new StrataNoise(seed), new WoodNoise(seed), new GaborNoise(seed), new MarbleNoise(seed),
                new ScalesNoise(seed), new ChladniNoise(seed), new KaleidoscopeNoise(seed), new MengerSpongeNoise(seed), new CircuitNoise(seed)};
    }

    private static void assertUnit(PatternNoise noise, double value) {
        assertTrue(noise.getClass().getSimpleName() + ": " + value,
                Double.isFinite(value) && value >= 0D && value <= 1D);
    }
}
