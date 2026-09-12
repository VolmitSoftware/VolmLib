package art.arcane.volmlib.util.noise;

import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TextureNoiseTest {
    @Test
    public void texturesKeepUnitRangeAndExactHorizontalSlicesThroughSixteenOctaves() {
        SplittableRandom random = new SplittableRandom(715L);
        for (long seed : new long[]{0L, 1337L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (PatternNoise noise : textures(seed)) {
                for (int octaves : new int[]{1, 4, 16}) {
                    noise.setOctaves(octaves);
                    for (int index = 0; index < 128; index++) {
                        double x = random.nextDouble(-468750D, 468750D);
                        double y = random.nextDouble(-32D, 32D);
                        double z = random.nextDouble(-468750D, 468750D);
                        double value = noise.noise(x, y, z);
                        assertTrue(noise.getClass().getSimpleName(), Double.isFinite(value) && value >= 0D && value <= 1D);
                        assertEquals(noise.noise(x, z), noise.noise(x, 0D, z), 0D);
                        assertEquals(noise.noise(x), noise.noise(x, 0D, 0D), 0D);
                    }
                }
            }
        }
    }

    @Test
    public void texturesUseTheFullSeedAndEveryCoordinate() {
        PatternNoise[] first = textures(1337L);
        PatternNoise[] same = textures(1337L);
        PatternNoise[] highSeed = textures(1337L + (1L << 32));
        for (int style = 0; style < first.length; style++) {
            double seedDifference = 0D;
            double xDifference = 0D;
            double yDifference = 0D;
            double zDifference = 0D;
            for (int index = 0; index < 256; index++) {
                double x = index * 0.073D - 4D;
                double y = index * 0.011D - 1D;
                double z = index * -0.113D + 3D;
                double value = first[style].noise(x, y, z);
                assertEquals(value, same[style].noise(x, y, z), 0D);
                seedDifference += Math.abs(value - highSeed[style].noise(x, y, z));
                xDifference += Math.abs(value - first[style].noise(x + 0.31D, y, z));
                yDifference += Math.abs(value - first[style].noise(x, y + 0.37D, z));
                zDifference += Math.abs(value - first[style].noise(x, y, z + 0.41D));
            }
            assertTrue(first[style].getClass().getSimpleName(), seedDifference > 4D);
            assertTrue(first[style].getClass().getSimpleName(), xDifference > 4D);
            assertTrue(first[style].getClass().getSimpleName(), yDifference > 4D);
            assertTrue(first[style].getClass().getSimpleName(), zDifference > 4D);
        }
    }

    @Test
    public void octaveChangesAddDetailAndRestoreTheOriginalSamples() {
        for (PatternNoise noise : textures(701L)) {
            double[] original = new double[128];
            for (int index = 0; index < original.length; index++) {
                original[index] = noise.noise(index * 0.071D, 0.193D, index * -0.113D);
            }
            noise.setOctaves(4);
            double difference = 0D;
            for (int index = 0; index < original.length; index++) {
                difference += Math.abs(original[index] - noise.noise(index * 0.071D, 0.193D, index * -0.113D));
            }
            assertTrue(noise.getClass().getSimpleName(), difference > 1D);
            noise.setOctaves(0);
            for (int index = 0; index < original.length; index++) {
                assertEquals(original[index], noise.noise(index * 0.071D, 0.193D, index * -0.113D), 0D);
            }
            noise.setOctaves(16);
            double maximum = noise.noise(0.173D, 0.193D, -0.391D);
            noise.setOctaves(Integer.MAX_VALUE);
            assertEquals(maximum, noise.noise(0.173D, 0.193D, -0.391D), 0D);
        }
    }

    @Test
    public void textureSamplingHasNoIntegerCellSeams() {
        for (PatternNoise noise : textures(913L)) {
            for (int boundary = -4; boundary <= 4; boundary++) {
                for (int index = 0; index < 64; index++) {
                    double first = index * 0.073D - 2D;
                    double second = index * -0.113D + 3D;
                    assertEquals(noise.noise(boundary - 1.0E-8D, first, second),
                            noise.noise(boundary + 1.0E-8D, first, second), 1.0E-5D);
                    assertEquals(noise.noise(first, boundary - 1.0E-8D, second),
                            noise.noise(first, boundary + 1.0E-8D, second), 1.0E-5D);
                    assertEquals(noise.noise(first, second, boundary - 1.0E-8D),
                            noise.noise(first, second, boundary + 1.0E-8D), 1.0E-5D);
                }
            }
        }
    }

    private static PatternNoise[] textures(long seed) {
        return new PatternNoise[]{new GaborNoise(seed), new MarbleNoise(seed), new ScalesNoise(seed)};
    }
}
