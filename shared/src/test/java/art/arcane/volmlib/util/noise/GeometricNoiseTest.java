package art.arcane.volmlib.util.noise;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeometricNoiseTest {
    private static final double SQRT_3 = Math.sqrt(3D);
    private static final long[] SEEDS = {0L, -1L, 1337L, Long.MIN_VALUE, Long.MAX_VALUE};

    @Test
    public void regularHexagonsMatchNearestLatticeCenters() {
        NoiseGenerator[] noises = {new HexagonNoise(177L), new HexSimplexNoise(311L)};
        for (NoiseGenerator noise : noises) {
            for (double x = -5.93D; x < 6D; x += 0.137D) {
                for (double z = -5.97D; z < 6D; z += 0.173D) {
                    double bestDistance = Double.POSITIVE_INFINITY;
                    double centerX = 0D;
                    double centerZ = 0D;
                    for (int q = -8; q <= 8; q++) {
                        for (int r = -8; r <= 8; r++) {
                            double candidateX = SQRT_3 * (q + (r * 0.5D));
                            double candidateZ = 1.5D * r;
                            double dx = x - candidateX;
                            double dz = z - candidateZ;
                            double distance = (dx * dx) + (dz * dz);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                centerX = candidateX;
                                centerZ = candidateZ;
                            }
                        }
                    }
                    assertEquals(noise.noise(centerX, centerZ), noise.noise(x, z), 0D);
                    assertEquals(noise.noise(centerX, 2.75D, centerZ), noise.noise(x, 2.75D, z), 0D);
                }
            }
        }
    }

    @Test
    public void hexagonNeighborsFollowCoherentHeat() {
        HexagonNoise noise = new HexagonNoise(7331L);
        double adjacentDifference = 0D;
        double distantDifference = 0D;
        for (int q = -32; q <= 32; q++) {
            for (int r = -32; r <= 32; r++) {
                double x = SQRT_3 * (q + (r * 0.5D));
                double z = 1.5D * r;
                double sample = noise.noise(x, z);
                adjacentDifference += Math.abs(sample - noise.noise(x + SQRT_3, z));
                distantDifference += Math.abs(sample - noise.noise(x + (SQRT_3 * 19D), z));
            }
        }
        assertTrue(adjacentDifference < distantDifference * 0.85D);
    }

    @Test
    public void nestedHexagonsHaveRegularInscribedFootprints() {
        for (int child = 0; child < 7; child++) {
            double angle = Math.toRadians((child * 60D) - 90D);
            double centerX = child == 0 ? 0D : Math.cos(angle) * (2D / 3D);
            double centerZ = child == 0 ? 0D : Math.sin(angle) * (2D / 3D);
            for (int direction = 0; direction < 60; direction++) {
                double sampleAngle = Math.toRadians(direction * 6D);
                double x = centerX + (Math.cos(sampleAngle) * 0.27D);
                double z = centerZ + (Math.sin(sampleAngle) * 0.27D);
                double q = (x / SQRT_3) - (z / 3D);
                double r = 2D * z / 3D;
                assertEquals(child, HexagonalNoise.pickChildIndex(q, r));
                assertEquals(0L, HexagonalNoise.roundQ(q, r));
                assertEquals(0L, HexagonalNoise.roundR(q, r));
            }
        }
    }

    @Test
    public void sierpinskiCutsTriangleCentroidsAtEveryDepth() {
        SierpinskiTriangleNoise noise = new SierpinskiTriangleNoise(91L);
        for (int depth = 0; depth < 4; depth++) {
            double size = Math.scalb(1D, -depth);
            double holeU = size / 3D;
            double holeV = size / 3D;
            assertTrue(sampleTriangle(noise, holeU, holeV) <= 0.12D);
            assertTrue(sampleTriangle(noise, 1D - size + holeU, holeV) <= 0.12D);
            assertTrue(sampleTriangle(noise, holeU, 1D - size + holeV) <= 0.12D);
        }
        assertTrue(sampleTriangle(noise, 1D / 48D, 1D / 48D) >= 0.35D);
        assertTrue(sampleTriangle(noise, 46D / 48D, 1D / 48D) >= 0.35D);
        assertTrue(sampleTriangle(noise, 1D / 48D, 46D / 48D) >= 0.35D);
    }

    @Test
    public void sierpinskiTilesAcrossNegativeCoordinatesWithExpectedArea() {
        SierpinskiTriangleNoise noise = new SierpinskiTriangleNoise(9123L);
        Random random = new Random(8211L);
        int filled = 0;
        int samples = 65536;
        for (int index = 0; index < samples; index++) {
            double u = random.nextDouble();
            double v = random.nextDouble();
            double x = 8D * (u + (v * 0.5D));
            double z = 4D * SQRT_3 * v;
            boolean inside = noise.noise(x, z) >= 0.35D;
            if (inside) {
                filled++;
            }
            assertEquals(inside, noise.noise(x - 8D, z) >= 0.35D);
            assertEquals(inside, noise.noise(x - 4D, z - (4D * SQRT_3)) >= 0.35D);
        }
        assertEquals(Math.pow(0.75D, 4), filled / (double) samples, 0.006D);
    }

    @Test
    public void dimensionalSlicesAreConsistentAndContinuous() {
        for (NoiseGenerator noise : generators(1337L)) {
            assertEquals(noise.noise(3.125D), noise.noise(3.125D, 0D), 0D);
            assertEquals(noise.noise(3.125D, -1.75D), noise.noise(3.125D, 0D, -1.75D), 0D);
            assertEquals(noise.noise(3.125D, 1.75D, 0D), noise.noise(3.125D, 1.75D, 1E-9D), 1E-7D);
            assertEquals(noise.noise(3.125D, 0D, -1.75D), noise.noise(3.125D, 1E-9D, -1.75D), 1E-7D);
        }
    }

    @Test
    public void seedsAndWorldBorderCoordinatesRemainDeterministicAndBounded() {
        double[][] points = {{0D, 0D, 0D}, {-29_999_984D, -64D, 29_999_984D},
                {3.125D, 17.375D, -9.25D}, {29_999_984D, 319D, -29_999_984D}};
        for (long seed : SEEDS) {
            NoiseGenerator[] first = generators(seed);
            NoiseGenerator[] second = generators(seed);
            for (int index = 0; index < first.length; index++) {
                if (first[index] instanceof OctaveNoise octaves) {
                    octaves.setOctaves(16);
                    ((OctaveNoise) second[index]).setOctaves(16);
                }
                for (double[] point : points) {
                    double value = first[index].noise(point[0], point[1], point[2]);
                    assertTrue(Double.isFinite(value) && value >= 0D && value <= 1D);
                    assertEquals(value, second[index].noise(point[0], point[1], point[2]), 0D);
                }
            }
        }
    }

    private double sampleTriangle(SierpinskiTriangleNoise noise, double u, double v) {
        return noise.noise(8D * (u + (v * 0.5D)), 4D * SQRT_3 * v);
    }

    private NoiseGenerator[] generators(long seed) {
        return new NoiseGenerator[]{new HexagonNoise(seed), new HexSimplexNoise(seed),
                new HexRandomSizeNoise(seed), new HexJamesNoise(seed), new SierpinskiTriangleNoise(seed)};
    }
}
