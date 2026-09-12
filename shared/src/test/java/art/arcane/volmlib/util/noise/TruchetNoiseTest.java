package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TruchetNoiseTest {
    @Test
    public void quarterCircleRibbonsMeetAcrossTileBoundaries() {
        TruchetNoise xBoundaryNoise = new TruchetNoise(0L);
        TruchetNoise zBoundaryNoise = new TruchetNoise(-0x9E3779B97F4A7C15L);
        double epsilon = 1.0E-8D;
        double maximum = 0D;

        for (int cell = -3; cell <= 3; cell++) {
            for (int index = 0; index <= 2048; index++) {
                double position = index / 512D - 2D;
                double x = cell - 0.17D;
                double z = cell - 0.13D;
                assertEquals(xBoundaryNoise.noise(x - epsilon, position),
                        xBoundaryNoise.noise(x + epsilon, position), 1.0E-5D);
                assertEquals(zBoundaryNoise.noise(position, z - epsilon),
                        zBoundaryNoise.noise(position, z + epsilon), 1.0E-5D);
                maximum = Math.max(maximum, xBoundaryNoise.noise(x, position));
            }
        }

        assertTrue(maximum > 0.999D);
    }

    @Test
    public void helicalContinuationIsContinuousThroughTheHorizontalSlice() {
        TruchetNoise noise = new TruchetNoise(713L);
        double heightVariation = 0D;
        double seedVariation = 0D;
        TruchetNoise otherSeed = new TruchetNoise(714L);

        for (int index = 0; index < 256; index++) {
            double x = index * 0.037D - 4D;
            double z = index * -0.051D + 7D;
            double horizontal = noise.noise(x, z);
            assertEquals(horizontal, noise.noise(x, 0D, z), 0D);
            assertEquals(horizontal, noise.noise(x, -1.0E-8D, z), 1.0E-5D);
            assertEquals(horizontal, noise.noise(x, 1.0E-8D, z), 1.0E-5D);
            heightVariation += Math.abs(horizontal - noise.noise(x, 1.25D, z));
            seedVariation += Math.abs(noise.noise(x) - otherSeed.noise(x));
        }

        assertTrue(heightVariation > 10D);
        assertTrue(seedVariation > 10D);
    }

    @Test
    public void octavesStayBoundedAndRepeatableAtWorldBorders() {
        TruchetNoise first = new TruchetNoise(Long.MIN_VALUE);
        TruchetNoise second = new TruchetNoise(Long.MIN_VALUE);

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
