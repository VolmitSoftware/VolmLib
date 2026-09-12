package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MengerSpongeNoiseTest {
    @Test
    public void threeSubdivisionsRetainExactlyTwentyCubedCells() {
        MengerSpongeNoise noise = new MengerSpongeNoise(0L);
        int retained = 0;
        for (int x = 0; x < 27; x++) {
            for (int y = 0; y < 27; y++) {
                for (int z = 0; z < 27; z++) {
                    retained += (int) noise.noise((x + 0.5D) / 27D,
                            (y + 0.5D) / 27D, (z + 0.5D) / 27D);
                }
            }
        }
        assertEquals(8000, retained);
    }

    @Test
    public void faceSliceContainsThreeLevelsOfSquareHoles() {
        MengerSpongeNoise noise = new MengerSpongeNoise(0L);
        assertEquals(0D, noise.noise(0.5D, 0D, 0.5D), 0D);
        assertEquals(0D, noise.noise(1D / 6D, 0D, 1D / 6D), 0D);
        assertEquals(0D, noise.noise(1D / 18D, 0D, 1D / 18D), 0D);
        assertEquals(1D, noise.noise(1D / 54D, 0D, 1D / 54D), 0D);
        assertEquals(0D, noise.noise(0.5D, 0.5D, 0.05D), 0D);
        assertEquals(0D, noise.noise(0.05D, 0.5D, 0.5D), 0D);
        int retained = 0;
        for (int x = 0; x < 27; x++) {
            for (int z = 0; z < 27; z++) {
                retained += (int) noise.noise((x + 0.5D) / 27D, (z + 0.5D) / 27D);
            }
        }
        assertEquals(512, retained);
    }

    @Test
    public void negativeTilesRepeatAndAllAxesAndUpperSeedBitsAffectGeometry() {
        MengerSpongeNoise first = new MengerSpongeNoise(1337L);
        MengerSpongeNoise second = new MengerSpongeNoise(1337L + (1L << 32));
        double xVariation = 0D;
        double yVariation = 0D;
        double zVariation = 0D;
        double seedVariation = 0D;
        for (int index = 0; index < 512; index++) {
            double x = index * 0.019D - 4D;
            double z = index * -0.031D + 2D;
            double value = first.noise(x, z);
            assertEquals(value, first.noise(x, 0D, z), 0D);
            assertEquals(value, first.noise(x - 2D, 0D, z + 3D), 0D);
            xVariation += Math.abs(value - first.noise(x + 0.137D, z));
            yVariation += Math.abs(value - first.noise(x, 0.419D, z));
            zVariation += Math.abs(value - first.noise(x, z + 0.237D));
            seedVariation += Math.abs(value - second.noise(x, z));
        }
        assertTrue(xVariation > 20D);
        assertTrue(yVariation > 20D);
        assertTrue(zVariation > 20D);
        assertTrue(seedVariation > 20D);
    }

    @Test
    public void octaveSamplesStayBoundedAndRepeatableAtWorldBorders() {
        MengerSpongeNoise first = new MengerSpongeNoise(Long.MAX_VALUE);
        MengerSpongeNoise second = new MengerSpongeNoise(Long.MAX_VALUE);
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
