package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VortexNoiseTest {
    @Test
    public void spiralsStayContinuousAcrossCellBoundaries() {
        for (long seed : new long[]{0L, 42L, Long.MAX_VALUE}) {
            VortexNoise noise = new VortexNoise(seed);
            for (int cell = -8; cell <= 8; cell++) {
                for (int index = 0; index < 64; index++) {
                    double coordinate = index * 0.0713D - 2D;
                    assertEquals(noise.noise(cell - 1E-7D, 0.317D, coordinate),
                            noise.noise(cell + 1E-7D, 0.317D, coordinate), 1E-5D);
                    assertEquals(noise.noise(coordinate, 0.317D, cell - 1E-7D),
                            noise.noise(coordinate, 0.317D, cell + 1E-7D), 1E-5D);
                }
            }
        }
    }

    @Test
    public void spiralCentersAndAngleWrapHaveNoJump() {
        VortexNoise noise = new VortexNoise(0L);
        double center = noise.noise(0.2D, 0.419D, 0.2D);
        for (int index = 0; index < 128; index++) {
            double angle = index * Math.PI / 64D;
            assertEquals(center, noise.noise(0.2D + Math.cos(angle) * 1E-8D, 0.419D,
                    0.2D + Math.sin(angle) * 1E-8D), 1E-6D);
            double x = 0.2D - index * 0.007D;
            assertEquals(noise.noise(x, 0.419D, 0.2D - 1E-8D),
                    noise.noise(x, 0.419D, 0.2D + 1E-8D), 1E-6D);
        }
    }
}
