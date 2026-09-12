package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CloverNoiseBoundaryTest {
    @Test
    public void threeDimensionalNoiseRemainsContinuousAcrossZeroZ() {
        for (long seed : new long[]{0L, 1337L, Long.MIN_VALUE}) {
            CloverNoise noise = new CloverNoise(seed);
            for (double x = -3.125D; x < 4D; x += 0.375D) {
                double atZero = noise.noise(x, 0.417D, 0D);
                assertEquals(atZero, noise.noise(x, 0.417D, -1E-7D), 1E-5D);
                assertEquals(atZero, noise.noise(x, 0.417D, 1E-7D), 1E-5D);
            }
        }
    }

    @Test(timeout = 5000)
    public void fractalIterationsCannotDivideByZeroOrRunUnbounded() {
        CloverNoise.Noise2D twoDimensional = new CloverNoise.Noise2D(1337L);
        CloverNoise.Noise3D threeDimensional = new CloverNoise.Noise3D(1337L);
        assertEquals(twoDimensional.noise(0.25D, -0.75D),
                twoDimensional.fractalNoise(0.25D, -0.75D, 0), 0D);
        assertEquals(threeDimensional.noise(0.25D, 0.5D, -0.75D),
                threeDimensional.fractalNoise(0.25D, 0.5D, -0.75D, Integer.MIN_VALUE), 0D);
        double bounded2D = twoDimensional.fractalNoise(0.25D, -0.75D, 16);
        double bounded3D = threeDimensional.fractalNoise(0.25D, 0.5D, -0.75D, 16);
        assertEquals(bounded2D, twoDimensional.fractalNoise(0.25D, -0.75D, Integer.MAX_VALUE), 0D);
        assertEquals(bounded3D, threeDimensional.fractalNoise(0.25D, 0.5D, -0.75D, Integer.MAX_VALUE), 0D);
        assertTrue(Double.isFinite(bounded2D));
        assertTrue(Double.isFinite(bounded3D));
    }
    @Test
    public void stationaryCurlVectorsNormalizeToZero() {
        CloverNoise.Vector2 twoDimensional = new CloverNoise.Vector2().normalize();
        CloverNoise.Vector3 threeDimensional = new CloverNoise.Vector3().normalize();
        assertEquals(0D, twoDimensional.getX(), 0D);
        assertEquals(0D, twoDimensional.getY(), 0D);
        assertEquals(0D, threeDimensional.getX(), 0D);
        assertEquals(0D, threeDimensional.getY(), 0D);
        assertEquals(0D, threeDimensional.getZ(), 0D);
    }
}
