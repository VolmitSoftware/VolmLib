package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WavePatternNoiseTest {
    private static final long SEED = 813753L;

    @Test
    public void wavePatternsAreSeededBoundedAndContinuousInEveryAxis() {
        for (boolean gyroid : new boolean[]{true, false}) {
            NoiseGenerator first = gyroid ? new GyroidNoise(SEED) : new QuasicrystalNoise(SEED);
            NoiseGenerator repeated = gyroid ? new GyroidNoise(SEED) : new QuasicrystalNoise(SEED);
            NoiseGenerator otherSeed = gyroid ? new GyroidNoise(SEED + 1) : new QuasicrystalNoise(SEED + 1);
            double seedDifference = 0D;
            double horizontalDifference = 0D;
            double verticalDifference = 0D;
            double depthDifference = 0D;
            for (double origin : new double[]{-30_000_000D / 64D, 0D, 30_000_000D / 64D}) {
                for (int i = 0; i < 128; i++) {
                    double x = origin + i * 0.137D;
                    double y = i * 0.193D - 7D;
                    double z = -origin + i * 0.317D;
                    double value = first.noise(x, y, z);
                    assertTrue(Double.isFinite(value) && value >= 0D && value <= 1D);
                    assertEquals(value, repeated.noise(x, y, z), 0D);
                    assertEquals(value, first.noise(x + 1E-6D, y, z), 1E-3D);
                    assertEquals(value, first.noise(x, y + 1E-6D, z), 1E-3D);
                    assertEquals(value, first.noise(x, y, z + 1E-6D), 1E-3D);
                    seedDifference += Math.abs(value - otherSeed.noise(x, y, z));
                    horizontalDifference += Math.abs(value - first.noise(x + 0.137D, y, z));
                    verticalDifference += Math.abs(value - first.noise(x, y + 0.137D, z));
                    depthDifference += Math.abs(value - first.noise(x, y, z + 0.137D));
                }
            }
            assertTrue(seedDifference > 10D);
            assertTrue(horizontalDifference > 10D);
            assertTrue(verticalDifference > 10D);
            assertTrue(depthDifference > 10D);
        }
    }

    @Test
    public void quasicrystalPreservesFivefoldRotationalSymmetryAcrossLayers() {
        QuasicrystalNoise noise = new QuasicrystalNoise(SEED);
        double angle = Math.PI * 2D / 5D;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        for (int i = 0; i < 128; i++) {
            double x = i * 0.137D - 8D;
            double y = i * 0.031D;
            double z = i * 0.219D - 13D;
            double rotatedX = x * cosine - z * sine;
            double rotatedZ = x * sine + z * cosine;
            assertEquals(noise.noise(x, y, z), noise.noise(rotatedX, y, rotatedZ), 1E-12D);
        }
        assertNotEquals(noise.noise(0.19D, -0.37D), noise.noise(1.19D, -0.37D), 1E-4D);
    }

    @Test
    public void gyroidContainsBothOpenSpacesAndBrightSheets() {
        GyroidNoise noise = new GyroidNoise(SEED);
        int open = 0;
        int sheet = 0;
        int gradient = 0;
        for (int x = -64; x < 64; x++) {
            for (int z = -64; z < 64; z++) {
                double value = noise.noise(x * 0.127D, z * 0.127D);
                if (value < 0.05D) {
                    open++;
                } else if (value > 0.8D) {
                    sheet++;
                } else {
                    gradient++;
                }
            }
        }
        assertTrue(open > 1638 && open < 13_107);
        assertTrue(sheet > 819);
        assertTrue(gradient > 1638);
    }
}
