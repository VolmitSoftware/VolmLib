package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KaleidoscopeNoiseTest {
    @Test
    public void wedgesMirrorAndRepeatAroundTheTileCenter() {
        KaleidoscopeNoise noise = new KaleidoscopeNoise(0L);
        for (int index = 0; index < 128; index++) {
            double angle = index * 0.037D;
            double radius = 0.05D + index * (0.4D / 128D);
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            double value = noise.noise(0.5D + x, z);
            assertEquals(value, noise.noise(0.5D + x, -z), 1.0E-12D);
            assertEquals(value, noise.noise(0.5D - z, x), 1.0E-12D);
        }
    }

    @Test
    public void compactMotifsMeetContinuouslyAtTileAndWedgeBoundaries() {
        double epsilon = 1.0E-8D;
        for (long seed : new long[]{0L, 1337L, Long.MIN_VALUE}) {
            KaleidoscopeNoise noise = new KaleidoscopeNoise(seed);
            for (int index = 0; index < 256; index++) {
                double position = index * 0.027D - 3D;
                double height = index * 0.017D - 2D;
                assertEquals(noise.noise(-epsilon, height, position),
                        noise.noise(epsilon, height, position), 1.0E-6D);
                assertEquals(noise.noise(position, height, 0.5D - epsilon),
                        noise.noise(position, height, 0.5D + epsilon), 1.0E-6D);
                assertEquals(noise.noise(0.5D - epsilon, height, 0D),
                        noise.noise(0.5D + epsilon, height, 0D), 1.0E-5D);
            }
        }
        KaleidoscopeNoise noise = new KaleidoscopeNoise(0L);
        for (double boundary : new double[]{-Math.PI, -Math.PI * 0.75D, 0D, Math.PI * 0.25D, Math.PI}) {
            assertEquals(noise.noise(0.5D + 0.3D * Math.cos(boundary - epsilon),
                            0.3D * Math.sin(boundary - epsilon)),
                    noise.noise(0.5D + 0.3D * Math.cos(boundary + epsilon),
                            0.3D * Math.sin(boundary + epsilon)), 1.0E-5D);
        }
    }

    @Test
    public void heightAndUpperSeedBitsChangeTheMotifs() {
        KaleidoscopeNoise first = new KaleidoscopeNoise(1337L);
        KaleidoscopeNoise second = new KaleidoscopeNoise(1337L + (1L << 32));
        double heightVariation = 0D;
        double seedVariation = 0D;
        double maximum = 0D;
        for (int index = 0; index < 512; index++) {
            double x = index * 0.037D - 5D;
            double z = index * -0.023D + 3D;
            double value = first.noise(x, z);
            assertEquals(value, first.noise(x, 0D, z), 0D);
            assertEquals(value, first.noise(x, 1.0E-8D, z), 1.0E-5D);
            heightVariation += Math.abs(value - first.noise(x, 1.25D, z));
            seedVariation += Math.abs(value - second.noise(x, z));
            maximum = Math.max(maximum, value);
        }
        assertTrue(heightVariation > 10D);
        assertTrue(seedVariation > 10D);
        assertTrue(maximum > 0.7D);
        double oneDimensionalVariation = 0D;
        for (int index = 0; index < 256; index++) {
            oneDimensionalVariation += Math.abs(first.noise(index * 0.013D)
                    - second.noise(index * 0.013D));
        }
        assertTrue(oneDimensionalVariation > 10D);
    }

    @Test
    public void octaveSamplesStayBoundedAndRepeatableAtWorldBorders() {
        KaleidoscopeNoise first = new KaleidoscopeNoise(Long.MAX_VALUE);
        KaleidoscopeNoise second = new KaleidoscopeNoise(Long.MAX_VALUE);
        for (int octaves : new int[]{1, 3, 16}) {
            first.setOctaves(octaves);
            second.setOctaves(octaves);
            for (int index = 0; index < 128; index++) {
                double x = -468750D + index * (937500D / 127D);
                double value = first.noise(x, x * 0.37D, x * -0.71D);
                assertTrue(Double.isFinite(value));
                assertTrue(value >= 0D && value <= 1D);
                assertEquals(value, second.noise(x, x * 0.37D, x * -0.71D), 0D);
            }
        }
    }
}
