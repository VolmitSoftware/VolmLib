package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CraterNoiseTest {
    @Test
    public void craterCentersSitInsideRaisedSphericalRims() {
        CraterNoise noise = new CraterNoise(0L);
        double center = noise.noise(0.2D, 0.2D, 0.2D);

        assertTrue(center < 0.4D);
        assertTrue(noise.noise(0.56D, 0.2D, 0.2D) > center + 0.2D);
        assertTrue(noise.noise(0.2D, 0.56D, 0.2D) > center + 0.2D);
        assertTrue(noise.noise(0.2D, 0.2D, 0.56D) > center + 0.2D);
    }

    @Test
    public void neighboringCellSearchDoesNotLeaveTileSeams() {
        double epsilon = 1.0E-8D;
        for (long seed : new long[]{0L, 713L, Long.MIN_VALUE}) {
            CraterNoise noise = new CraterNoise(seed);
            for (int boundary = -3; boundary <= 3; boundary++) {
                for (int index = 0; index < 64; index++) {
                    double first = index * 0.071D - 2D;
                    double second = index * -0.093D + 3D;
                    assertEquals(noise.noise(boundary - epsilon, first, second),
                            noise.noise(boundary + epsilon, first, second), 1.0E-5D);
                    assertEquals(noise.noise(first, boundary - epsilon, second),
                            noise.noise(first, boundary + epsilon, second), 1.0E-5D);
                    assertEquals(noise.noise(first, second, boundary - epsilon),
                            noise.noise(first, second, boundary + epsilon), 1.0E-5D);
                }
            }
        }
    }

    @Test
    public void horizontalSliceHasDistinctBowlsAndAnnularRims() {
        CraterNoise noise = new CraterNoise(713L);
        CraterNoise otherSeed = new CraterNoise(714L);
        double minimum = 1D;
        double maximum = 0D;
        double heightVariation = 0D;
        double seedVariation = 0D;

        for (int index = 0; index < 1024; index++) {
            double x = index * 0.037D - 4D;
            double z = index * -0.051D + 7D;
            double value = noise.noise(x, z);
            assertEquals(value, noise.noise(x, 0D, z), 0D);
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            heightVariation += Math.abs(value - noise.noise(x, 1.25D, z));
            seedVariation += Math.abs(noise.noise(x) - otherSeed.noise(x));
        }

        assertTrue(minimum < 0.3D);
        assertTrue(maximum > 0.7D);
        assertTrue(heightVariation > 10D);
        assertTrue(seedVariation > 10D);
    }

    @Test
    public void octaveSamplesStayBoundedAndRepeatableAtWorldBorders() {
        CraterNoise first = new CraterNoise(Long.MAX_VALUE);
        CraterNoise second = new CraterNoise(Long.MAX_VALUE);

        for (int octaves : new int[]{1, 4, 16}) {
            first.setOctaves(octaves);
            second.setOctaves(octaves);
            for (double coordinate : new double[]{-468750D, -1D, -0D, 0.125D, 1D, 468750D}) {
                double value = first.noise(coordinate, coordinate * 0.37D, coordinate * -0.71D);
                assertTrue(Double.isFinite(value));
                assertTrue(value >= 0D && value <= 1D);
                assertEquals(value, second.noise(coordinate, coordinate * 0.37D, coordinate * -0.71D), 0D);
            }
        }
    }
}
