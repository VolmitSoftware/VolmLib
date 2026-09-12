package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScalesNoiseTest {
    private static final double ZERO_SEED_PHASE_Z = 0.8833108082136426D;

    @Test
    public void groovesTraceStaggeredSemicirclesAroundRaisedFaces() {
        ScalesNoise noise = new ScalesNoise(0L);
        for (int row = -3; row <= 3; row++) {
            double x = (row & 1) * 0.5D;
            double z = row * 0.5D - ZERO_SEED_PHASE_Z;
            assertTrue(noise.noise(x, z + 0.2D) > 0.75D);
            for (int point = 1; point < 12; point++) {
                double angle = point * Math.PI / 12D;
                assertTrue(noise.noise(x + Math.cos(angle) * 0.5D, z + Math.sin(angle) * 0.5D) < 0.24D);
            }
        }
    }

    @Test
    public void staggeredRowAndColumnBoundariesRemainContinuous() {
        ScalesNoise noise = new ScalesNoise(0L);
        for (int row = -4; row <= 4; row++) {
            double z = row * 0.5D - ZERO_SEED_PHASE_Z;
            for (int point = 0; point < 512; point++) {
                double x = point / 128D - 2D;
                assertEquals(noise.noise(x, z - 1.0E-8D), noise.noise(x, z + 1.0E-8D), 1.0E-5D);
                assertEquals(noise.noise(row * 0.5D - 1.0E-8D, x),
                        noise.noise(row * 0.5D + 1.0E-8D, x), 1.0E-5D);
            }
        }
    }
}
