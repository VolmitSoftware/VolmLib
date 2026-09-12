package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MarbleNoiseTest {
    @Test
    public void narrowVeinsCrossPredominantlySoftStone() {
        MarbleNoise noise = new MarbleNoise(1337L);
        int dark = 0;
        int soft = 0;
        double minimum = 1D;
        double maximum = 0D;
        for (int row = 0; row < 128; row++) {
            for (int column = 0; column < 128; column++) {
                double value = noise.noise(column * 0.0625D - 4D, row * 0.0625D - 4D);
                dark += value < 0.3D ? 1 : 0;
                soft += value > 0.65D ? 1 : 0;
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
        }
        assertTrue(minimum < 0.15D);
        assertTrue(maximum > 0.85D);
        assertTrue(dark > 250 && dark < 2500);
        assertTrue(soft > 10000);
    }
}
