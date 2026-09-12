package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class CNGDimensionDispatchTest {
    private static final double EPSILON = 1.0E-12D;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void eachEntrypointUsesItsNativeDimensionWithScalingAndOpacity() {
        CoordinateNoise source = new CoordinateNoise();
        CNG noise = new CNG(new RNG(41L), source, 0.6D, 1).scale(0.375D).bake().scale(1.75D);
        double scale = 0.375D * 1.75D;

        for (double x : new double[]{-13.25D, 0D, 17.5D}) {
            double z = x + 2.75D;
            double y = x - 4.125D;
            double expected1D = source.noise(x * scale) * 0.6D;
            double expected2D = source.noise(x * scale, z * scale) * 0.6D;
            double expected3D = source.noise(x * scale, y * scale, z * scale) * 0.6D;

            assertEquals(expected1D, noise.noise(x), EPSILON);
            assertEquals(expected1D, noise.noiseFast1D(x), EPSILON);
            assertEquals(expected1D, noise.noise(new double[]{x}), EPSILON);
            assertEquals(expected2D, noise.noise(x, z), EPSILON);
            assertEquals(expected2D, noise.noiseFast2D(x, z), EPSILON);
            assertEquals(expected2D, noise.noise(new double[]{x, z}), EPSILON);
            assertEquals(expected2D * 2D - 1D, noise.noiseFastSigned2D(x, z), EPSILON);
            assertEquals(expected3D, noise.noise(x, y, z), EPSILON);
            assertEquals(expected3D, noise.noiseFast3D(x, y, z), EPSILON);
            assertEquals(expected3D, noise.noise(new double[]{x, y, z}), EPSILON);
            assertEquals(expected3D * 2D - 1D, noise.noiseFastSigned3D(x, y, z), EPSILON);
        }
    }

    @Test
    public void generatorsWithoutCoordinateScalingKeepTheirNativeInputs() {
        CoordinateNoise source = new CoordinateNoise() {
            @Override
            public boolean isNoScale() {
                return true;
            }
        };
        CNG fracture = new CNG(new RNG(2L), new CoordinateNoise(), 1D, 1);
        CNG noise = new CNG(new RNG(1L), source, 1D, 1).scale(7D).bake().fractureWith(fracture, 12D);

        assertEquals(source.noise(3D), noise.noiseFast1D(3D), EPSILON);
        assertEquals(source.noise(3D, 7D), noise.noiseFast2D(3D, 7D), EPSILON);
        assertEquals(source.noiseSigned(3D, 7D), noise.noiseFastSigned2D(3D, 7D), EPSILON);
        assertEquals(source.noise(3D, 5D, 7D), noise.noiseFast3D(3D, 5D, 7D), EPSILON);
        assertEquals(source.noiseSigned(3D, 5D, 7D), noise.noiseFastSigned3D(3D, 5D, 7D), EPSILON);
    }

    @Test
    public void fractureSamplesAndDisplacesTheRequestedDimensions() {
        CoordinateNoise source = new CoordinateNoise();
        CoordinateNoise fractureSource = new CoordinateNoise();
        CNG fracture = new CNG(new RNG(2L), fractureSource, 1D, 1);
        CNG noise = new CNG(new RNG(1L), source, 0.7D, 1).scale(0.35D).fractureWith(fracture, 12D);
        double x = -13.25D;
        double y = 5.5D;
        double z = 37.75D;
        double displaced1D = x + (fractureSource.noise(x) - 0.5D) * 12D;
        double displacedX2D = x + (fractureSource.noise(x, z) - 0.5D) * 12D;
        double displacedZ2D = z + (fractureSource.noise(z, x) - 0.5D) * 12D;
        double displacedX3D = x + (fractureSource.noise(x, y, z) - 0.5D) * 12D;
        double displacedY3D = y + (fractureSource.noise(y, x) - 0.5D) * 12D;
        double displacedZ3D = z + (fractureSource.noise(z, x, y) - 0.5D) * 12D;
        double expected1D = source.noise(displaced1D * 0.35D) * 0.7D;
        double expected2D = source.noise(displacedX2D * 0.35D, displacedZ2D * 0.35D) * 0.7D;
        double expected3D = source.noise(displacedX3D * 0.35D, displacedY3D * 0.35D, displacedZ3D * 0.35D) * 0.7D;

        assertEquals(expected1D, noise.noiseFast1D(x), EPSILON);
        assertEquals(expected1D, noise.noise(new double[]{x}), EPSILON);
        assertEquals(expected2D, noise.noiseFast2D(x, z), EPSILON);
        assertEquals(expected2D, noise.noise(new double[]{x, z}), EPSILON);
        assertEquals(expected2D * 2D - 1D, noise.noiseFastSigned2D(x, z), EPSILON);
        assertEquals(expected3D, noise.noiseFast3D(x, y, z), EPSILON);
        assertEquals(expected3D, noise.noise(new double[]{x, y, z}), EPSILON);
        assertEquals(expected3D * 2D - 1D, noise.noiseFastSigned3D(x, y, z), EPSILON);
    }

    @Test
    public void offsetsPreserveTheNativeCoordinatePlane() {
        long seed = 75L;
        RNG random = new RNG(seed);
        double offsetX = random.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
        double offsetZ = random.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
        CoordinateNoise source = new CoordinateNoise();
        CNG noise = new CNG(new RNG(9L), source.offset(seed), 1D, 1);

        assertEquals(source.noise(3D + offsetX), noise.noise(3D), EPSILON);
        assertEquals(source.noise(3D + offsetX, 7D + offsetZ), noise.noise(3D, 7D), EPSILON);
        assertEquals(source.noiseSigned(3D + offsetX, 7D + offsetZ), noise.noiseFastSigned2D(3D, 7D), EPSILON);
        assertEquals(source.noise(3D + offsetX, 5D, 7D + offsetZ), noise.noise(3D, 5D, 7D), EPSILON);
    }

    @Test
    public void zoomResizesSharedFracturesAndChildrenOnce() {
        CNG shared = new CNG(new RNG(71L), new CoordinateNoise(), 1D, 1).scale(0.7D);
        CNG noise = new CNG(new RNG(73L), new CoordinateNoise(), 1D, 1)
                .scale(0.8D).fractureWith(shared, 3D).child(shared);
        double one = noise.noise(7D);
        double two = noise.noise(7D, -11D);
        double three = noise.noise(7D, 17D, -11D);
        noise.zoom(2D);
        assertEquals(one, noise.noise(14D), EPSILON);
        assertEquals(two, noise.noise(14D, -22D), EPSILON);
        assertEquals(three, noise.noise(14D, 34D, -22D), EPSILON);
        assertEquals(two * 2D - 1D, noise.noiseFastSigned2D(14D, -22D), EPSILON);
        assertEquals(three * 2D - 1D, noise.noiseFastSigned3D(14D, 34D, -22D), EPSILON);
    }

    @Test
    public void zoomInvalidatesBakedAndSignedSamples() {
        CoordinateNoise source = new CoordinateNoise();
        CNG noise = new CNG(new RNG(31L), source, 1D, 1);
        noise.cached(4, "zoom", temporaryFolder.getRoot(), true);
        noise.noiseFastSigned2D(2D, 2D);
        noise.zoom(2D);
        assertEquals(source.noise(1D, 1D), noise.noise(2D, 2D), EPSILON);
        assertEquals(source.noiseSigned(1D, 1D), noise.noiseFastSigned2D(2D, 2D), EPSILON);
        for (double factor : new double[]{0D, -1D, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> noise.zoom(factor));
        }
    }

    @Test
    public void cachedIntegerAndUncachedFractionalSamplesUseTheSamePlane() {
        CoordinateNoise source = new CoordinateNoise();
        CNG noise = new CNG(new RNG(15L), source, 1D, 1);
        noise.noiseFastSigned2D(1D, 2D);
        noise.cached(4, "dimensions", temporaryFolder.getRoot(), true);
        double cached = (float) source.noise(1D, 2D);

        assertEquals(cached, noise.noise(1D, 2D), 0D);
        assertEquals(cached, noise.noiseFast2D(1D, 2D), 0D);
        assertEquals(cached, noise.noise(new double[]{1D, 2D}), 0D);
        assertEquals(cached * 2D - 1D, noise.noiseFastSigned2D(1D, 2D), 0D);
        assertEquals(source.noise(1.25D, 2.5D), noise.noiseFast2D(1.25D, 2.5D), EPSILON);
        assertEquals(source.noise(1D), noise.noise(1D), EPSILON);
        assertEquals(source.noise(1D, 3D, 2D), noise.noise(1D, 3D, 2D), EPSILON);

        noise.scale(0.5D);
        assertEquals(source.noise(0.5D, 1D), noise.noiseFast2D(1D, 2D), EPSILON);
        assertEquals(source.noiseSigned(0.5D, 1D), noise.noiseFastSigned2D(1D, 2D), EPSILON);
    }

    @Test
    public void cachedNoiseDoesNotMirrorOrRepeatOutsideItsBakedArea() {
        CNG uncached = new CNG(new RNG(15L), new CoordinateNoise(), 1D, 1);
        CNG cached = new CNG(new RNG(15L), new CoordinateNoise(), 1D, 1);
        cached.cached(4, "bounded-dimensions", temporaryFolder.getRoot(), true);
        double[] coordinates = {-8D, -4D, -1D, -0.25D, 0D, 0.25D, 3D, 3.75D, 4D, 4.25D, 8D, 29_999_999D};

        for (double x : coordinates) {
            assertEquals(uncached.noise(x), cached.noise(x), 0D);
            assertEquals(uncached.noiseFast1D(x), cached.noiseFast1D(x), 0D);
            assertEquals(uncached.noise(new double[]{x}), cached.noise(new double[]{x}), 0D);

            for (double z : coordinates) {
                double expected = uncached.noise(x, z);
                if (x >= 0D && x < 4D && z >= 0D && z < 4D && x == (int) x && z == (int) z) {
                    expected = (float) expected;
                }

                assertEquals(expected, cached.noise(x, z), 0D);
                assertEquals(expected, cached.noiseFast2D(x, z), 0D);
                assertEquals(expected, cached.noise(new double[]{x, z}), 0D);
                assertEquals(expected * 2D - 1D, cached.noiseFastSigned2D(x, z), EPSILON);
                assertEquals(uncached.noise(x, 5D, z), cached.noise(x, 5D, z), 0D);
                assertEquals(uncached.noiseFast3D(x, 5D, z), cached.noiseFast3D(x, 5D, z), 0D);
                assertEquals(uncached.noiseFastSigned3D(x, 5D, z), cached.noiseFastSigned3D(x, 5D, z), 0D);
                assertEquals(uncached.noise(new double[]{x, 5D, z}), cached.noise(new double[]{x, 5D, z}), 0D);
            }
        }
    }

    @Test
    public void octaveChangesReachNestedOffsetsAndInvalidateSignedSamples() {
        OctaveCoordinateNoise source = new OctaveCoordinateNoise();
        OffsetNoiseGenerator offset = source.offset(6L).offset(9L);
        CNG noise = new CNG(new RNG(77L), offset, 1D, 3);

        assertEquals(3, source.octaves);
        double previous2D = noise.noiseFastSigned2D(3D, 7D);
        double previous3D = noise.noiseFastSigned3D(3D, 5D, 7D);
        noise.oct(5);

        assertEquals(5, source.octaves);
        assertEquals(offset.noiseSigned(3D, 7D), noise.noiseFastSigned2D(3D, 7D), EPSILON);
        assertEquals(offset.noiseSigned(3D, 5D, 7D), noise.noiseFastSigned3D(3D, 5D, 7D), EPSILON);
        assertNotEquals(previous2D, noise.noiseFastSigned2D(3D, 7D), EPSILON);
        assertNotEquals(previous3D, noise.noiseFastSigned3D(3D, 5D, 7D), EPSILON);
    }

    @Test
    public void transformChangesInvalidateSignedSamples() {
        CNG noise = new CNG(new RNG(34L), new CoordinateNoise(), 1D, 1);
        double previous2D = noise.noiseFastSigned2D(3D, 7D);
        double previous3D = noise.noiseFastSigned3D(3D, 5D, 7D);
        noise.up(0.125D);

        assertEquals(previous2D + 0.25D, noise.noiseFastSigned2D(3D, 7D), EPSILON);
        assertEquals(previous3D + 0.25D, noise.noiseFastSigned3D(3D, 5D, 7D), EPSILON);
    }

    @Test
    public void invalidDimensionCountsFailExplicitly() {
        CNG noise = new CNG(new RNG(9L), new CoordinateNoise(), 1D, 1);

        assertThrows(IllegalArgumentException.class, () -> noise.noise(new double[0]));
        assertThrows(IllegalArgumentException.class, () -> noise.noise(new double[]{1D, 2D, 3D, 4D}));
    }

    private static class CoordinateNoise implements NoiseGenerator {
        @Override
        public double noise(double x) {
            return 0.125D + x * 0.001D;
        }

        @Override
        public double noise(double x, double z) {
            return 0.25D + x * 0.001D + z * 0.002D;
        }

        @Override
        public double noise(double x, double y, double z) {
            return 0.5D + x * 0.001D + y * 0.002D + z * 0.003D;
        }
    }

    private static final class OctaveCoordinateNoise extends CoordinateNoise implements OctaveNoise {
        private int octaves;

        @Override
        public double noise(double x, double z) {
            return super.noise(x, z) / octaves;
        }

        @Override
        public double noise(double x, double y, double z) {
            return super.noise(x, y, z) / octaves;
        }

        @Override
        public void setOctaves(int octaves) {
            this.octaves = octaves;
        }
    }
}
