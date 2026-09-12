package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CNGFastPathParityTest {
    @Test
    public void identityFastPathMatchesLegacyAcrossSeedAndCoordinateGrid() {
        for (long seed = 3L; seed <= 11L; seed++) {
            CNG generator = createIdentityGenerator(seed);
            assertFastPathParity("identity-seed-" + seed, generator);
        }
    }

    @Test
    public void transformedGeneratorsMatchLegacyAcrossSeedAndCoordinateGrid() {
        for (long seed = 21L; seed <= 27L; seed++) {
            List<CNG> generators = createTransformedGenerators(seed);
            for (int index = 0; index < generators.size(); index++) {
                assertFastPathParity("transformed-seed-" + seed + "-case-" + index, generators.get(index));
            }
        }
    }

    @Test
    public void signedFastPathMatchesLegacyAcrossRepresentativeGenerators() {
        for (long seed = 31L; seed <= 35L; seed++) {
            List<CNG> generators = createSignedGenerators(seed);
            for (int index = 0; index < generators.size(); index++) {
                assertSignedFastPathParity("signed-seed-" + seed + "-case-" + index, generators.get(index));
            }
        }
    }

    private void assertFastPathParity(String label, CNG generator) {
        for (int x = -320; x <= 320; x += 19) {
            for (int z = -320; z <= 320; z += 23) {
                double expected = generator.noise(x, z);
                double actual = generator.noiseFast2D(x, z);
                assertEquals(label + " 2D x=" + x + " z=" + z, expected, actual, 1.0E-12D);
            }
        }

        for (int x = -128; x <= 128; x += 17) {
            for (int y = -96; y <= 96; y += 13) {
                for (int z = -128; z <= 128; z += 19) {
                    double expected = generator.noise(x, y, z);
                    double actual = generator.noiseFast3D(x, y, z);
                    assertEquals(label + " 3D x=" + x + " y=" + y + " z=" + z, expected, actual, 1.0E-12D);
                }
            }
        }
    }

    private void assertSignedFastPathParity(String label, CNG generator) {
        for (int x = -320; x <= 320; x += 19) {
            for (int z = -320; z <= 320; z += 23) {
                double expected = (generator.noiseFast2D(x, z) * 2D) - 1D;
                double actual = generator.noiseFastSigned2D(x, z);
                assertEquals(label + " signed-2D x=" + x + " z=" + z, expected, actual, 1.0E-12D);
            }
        }

        for (int x = -128; x <= 128; x += 17) {
            for (int y = -96; y <= 96; y += 13) {
                for (int z = -128; z <= 128; z += 19) {
                    double expected = (generator.noiseFast3D(x, y, z) * 2D) - 1D;
                    double actual = generator.noiseFastSigned3D(x, y, z);
                    assertEquals(label + " signed-3D x=" + x + " y=" + y + " z=" + z, expected, actual, 1.0E-12D);
                }
            }
        }
    }

    private CNG createIdentityGenerator(long seed) {
        DeterministicNoiseGenerator generator = new DeterministicNoiseGenerator(0.31D + (seed * 0.01D));
        return new CNG(new RNG(seed), generator, 1D, 1).bake();
    }

    private List<CNG> createTransformedGenerators(long seed) {
        List<CNG> generators = new ArrayList<>();

        CNG powerTransformed = createIdentityGenerator(seed).pow(1.27D);
        generators.add(powerTransformed);

        CNG offsetTransformed = createIdentityGenerator(seed + 1L).up(0.08D).down(0.03D).patch(0.91D);
        generators.add(offsetTransformed);

        CNG fractured = createIdentityGenerator(seed + 2L).fractureWith(createIdentityGenerator(seed + 300L), 12.5D);
        generators.add(fractured);

        CNG withChildren = createIdentityGenerator(seed + 3L);
        withChildren.child(createIdentityGenerator(seed + 400L));
        withChildren.child(createIdentityGenerator(seed + 401L));
        generators.add(withChildren);

        return generators;
    }

    private List<CNG> createSignedGenerators(long seed) {
        List<CNG> generators = new ArrayList<>();

        generators.add(new CNG(new RNG(seed), new SimplexNoise(seed + 100L), 1D, 1).bake());
        generators.add(new CNG(new RNG(seed + 1L), new FractalFBMSimplexNoise(seed + 101L), 1D, 1).bake());
        generators.add(new CNG(new RNG(seed + 2L), new VascularNoise(seed + 102L), 1D, 1).bake());
        generators.add(new CNG(new RNG(seed + 3L), new OffsetNoiseGenerator(new SimplexNoise(seed + 103L), seed + 104L), 1D, 1).bake());
        generators.add(new CNG(new RNG(seed + 4L), new OffsetNoiseGenerator(new PerlinNoise(seed + 107L), seed + 108L), 1D, 1).bake());
        generators.add(new CNG(new RNG(seed + 5L), new OffsetNoiseGenerator(new VascularNoise(seed + 109L), seed + 110L), 1D, 1).bake());
        generators.add(new CNG(new RNG(seed + 6L), new OffsetNoiseGenerator(new FractalFBMSimplexNoise(seed + 111L), seed + 112L), 1D, 1).bake());

        CNG fractured = new CNG(new RNG(seed + 7L), new OffsetNoiseGenerator(new FractalFBMSimplexNoise(seed + 105L), seed + 106L), 1D, 1).bake();
        fractured.fractureWith(createIdentityGenerator(seed + 500L), 9.5D);
        generators.add(fractured);

        return generators;
    }

    private static class DeterministicNoiseGenerator implements NoiseGenerator {
        private final double offset;

        private DeterministicNoiseGenerator(double offset) {
            this.offset = offset;
        }

        @Override
        public double noise(double x) {
            double angle = (x * 0.011D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }

        @Override
        public double noise(double x, double z) {
            double angle = (x * 0.013D) + (z * 0.017D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }

        @Override
        public double noise(double x, double y, double z) {
            double angle = (x * 0.007D) + (y * 0.015D) + (z * 0.019D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }
    }
}
