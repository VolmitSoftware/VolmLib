package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WhiteNoiseTest {
    @Test
    public void scatterDoesNotRepeatEvery8192Blocks() {
        WhiteNoise noise = new WhiteNoise(7153L);
        for (double x : new double[]{-29999999.25D, -8192D, 0D, 37.5D, 8192D}) {
            assertNotEquals(noise.noise(x, 73D), noise.noise(x + 8192D, 73D), 0D);
            assertNotEquals(noise.noise(x, 53D, 73D), noise.noise(x, 53D, 73D + 8192D), 0D);
        }
    }

    @Test
    public void fractionalCoordinatesRemainDistinctNearWorldBorder() {
        WhiteNoise noise = new WhiteNoise(15273L);
        double x = 29999111.25D;
        assertNotEquals(noise.noise(x, -x), noise.noise(x + 0.0001D, -x), 0D);
        assertNotEquals(noise.noise(x, 71.5D, -x), noise.noise(x, 71.5001D, -x), 0D);
    }

    @Test
    public void scatterIsRepeatableAndUsesFullSeed() {
        WhiteNoise first = new WhiteNoise(725L);
        WhiteNoise same = new WhiteNoise(725L);
        WhiteNoise upperBits = new WhiteNoise(725L + (1L << 48));
        for (int index = 0; index < 512; index++) {
            double x = index * 1.25D;
            double value = first.noise(x, 53D, -x);
            assertEquals(value, same.noise(x, 53D, -x), 0D);
            assertTrue(value >= 0D && value <= 1D);
            assertNotEquals(value, upperBits.noise(x, 53D, -x), 0D);
        }
    }
}
