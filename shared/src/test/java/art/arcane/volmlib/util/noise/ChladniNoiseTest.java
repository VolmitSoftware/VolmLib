package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChladniNoiseTest {
    @Test
    public void horizontalPlateHasMirroredNodalLinesAndDarkAntinodes() {
        for (long seed : new long[]{0L, 1337L, Long.MIN_VALUE}) {
            ChladniNoise noise = new ChladniNoise(seed);
            double minimum = 1D;
            for (int index = 0; index < 256; index++) {
                double x = index * 0.031D - 4D;
                double z = index * -0.047D + 3D;
                assertEquals(1D, noise.noise(x, x), 0D);
                assertEquals(noise.noise(x, z), noise.noise(z, x), 1.0E-12D);
                minimum = Math.min(minimum, noise.noise(x, z));
            }
            assertTrue(minimum < 0.01D);
        }
    }

    @Test
    public void standingModesChangeContinuouslyWithHeight() {
        ChladniNoise noise = new ChladniNoise(1337L);
        double variation = 0D;
        for (int index = 0; index < 512; index++) {
            double x = index * 0.019D - 5D;
            double z = index * -0.023D + 3D;
            assertEquals(noise.noise(x, z), noise.noise(x, 0D, z), 0D);
            for (double y : new double[]{-2D, 0D, 1D, 468750D}) {
                assertEquals(noise.noise(x, y - 1.0E-8D, z),
                        noise.noise(x, y + 1.0E-8D, z), 1.0E-5D);
            }
            variation += Math.abs(noise.noise(x, z) - noise.noise(x, 0.7D, z));
        }
        assertTrue(variation > 20D);
    }

    @Test
    public void upperSeedBitsChangeThePlate() {
        ChladniNoise first = new ChladniNoise(1337L);
        ChladniNoise second = new ChladniNoise(1337L + (1L << 32));
        double variation = 0D;
        for (int index = 0; index < 256; index++) {
            variation += Math.abs(first.noise(index * 0.019D) - second.noise(index * 0.019D));
        }
        assertTrue(variation > 10D);
    }

    @Test
    public void octaveSamplesStayBoundedAndRepeatableAtWorldBorders() {
        ChladniNoise first = new ChladniNoise(Long.MAX_VALUE);
        ChladniNoise second = new ChladniNoise(Long.MAX_VALUE);
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
