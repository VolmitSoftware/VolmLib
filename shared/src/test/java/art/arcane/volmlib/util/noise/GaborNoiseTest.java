package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class GaborNoiseTest {
    @Test
    public void wavePacketsHaveDirectionalDetailAndQuietGaps() {
        GaborNoise noise = new GaborNoise(1337L);
        double xx = 0D;
        double xz = 0D;
        double zz = 0D;
        int quiet = 0;
        int dark = 0;
        int light = 0;
        for (int row = 0; row < 128; row++) {
            for (int column = 0; column < 128; column++) {
                double x = column * 0.0625D - 4D;
                double z = row * 0.0625D - 4D;
                double value = noise.noise(x, z);
                quiet += Math.abs(value - 0.5D) < 0.03D ? 1 : 0;
                dark += value < 0.3D ? 1 : 0;
                light += value > 0.7D ? 1 : 0;
                double dx = noise.noise(x + 0.001D, z) - noise.noise(x - 0.001D, z);
                double dz = noise.noise(x, z + 0.001D) - noise.noise(x, z - 0.001D);
                xx += dx * dx;
                xz += dx * dz;
                zz += dz * dz;
            }
        }
        double spread = Math.sqrt((xx - zz) * (xx - zz) + 4D * xz * xz);
        double major = xx + zz + spread;
        double minor = xx + zz - spread;
        assertTrue(major > minor * 3D);
        assertTrue(quiet > 1000);
        assertTrue(dark > 500);
        assertTrue(light > 500);
    }
}
