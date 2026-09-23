package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CNGCoordinateCacheTest {
    @Test
    public void alignedCoordinatesUseTheCacheAndRarelyCollideWithTheirTranspose() throws Exception {
        Method slot = CNG.class.getDeclaredMethod("coordSlot", long.class, long.class, int.class);
        slot.setAccessible(true);
        for (int salt : new int[]{1, 12345, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int spacing : new int[]{1, 4, 16, 64}) {
                BitSet occupied = new BitSet();
                int transposedCollisions = 0;
                for (int x = 0; x < 128; x++) {
                    for (int z = 0; z < 128; z++) {
                        long xBits = Double.doubleToRawLongBits((double) x * spacing);
                        long zBits = Double.doubleToRawLongBits((double) z * spacing);
                        int index = (int) slot.invoke(null, xBits, zBits, salt);
                        occupied.set(index);
                        if (x != z && index == (int) slot.invoke(null, zBits, xBits, salt)) {
                            transposedCollisions++;
                        }
                    }
                }
                assertTrue("occupied=" + occupied.cardinality(), occupied.cardinality() > 12_000);
                assertTrue("transpose collisions=" + transposedCollisions, transposedCollisions < 128);
            }
        }
    }

    @Test
    public void repeatedIntegerSweepsReuseMostSignedSamplesInBothDimensions() {
        for (boolean threeDimensions : new boolean[]{false, true}) {
            CountingNoise source = new CountingNoise();
            CNG noise = new CNG(new RNG(17L), source, 1D, 1);
            for (int pass = 0; pass < 2; pass++) {
                for (int x = 0; x < 128; x++) {
                    for (int z = 0; z < 128; z++) {
                        double actual = threeDimensions
                                ? noise.noiseFastSigned3D(x, 17D, z)
                                : noise.noiseFastSigned2D(x, z);
                        assertBits(x, actual);
                    }
                }
            }
            assertTrue("provider calls=" + source.calls, source.calls < 24_576);
        }
    }

    @Test
    public void cachedSamplesMatchUncachedSimplexAndBillowBits() {
        for (NoiseGenerator source : List.of(new SimplexNoise(19L), new FractalBillowSimplexNoise(31L))) {
            for (int octaves : new int[]{1, 3}) {
                CNG noise = new CNG(new RNG(17L), source, 1D, octaves);
                for (int pass = 0; pass < 2; pass++) {
                    for (int index = -128; index <= 128; index++) {
                        double x = index * 64D;
                        double z = index * -17D + 1D;
                        assertBits(source.noiseSigned(x, z), noise.noiseFastSigned2D(x, z));
                        assertBits(source.noiseSigned(x, 23D, z), noise.noiseFastSigned3D(x, 23D, z));
                    }
                }
            }
        }
    }

    @Test
    public void fullRawCoordinateBitsRemainDistinctForZerosAndNanPayloads() {
        CountingNoise source = new CountingNoise();
        CNG noise = new CNG(new RNG(17L), source, 1D, 1);
        double[] values = {+0D, -0D, Double.longBitsToDouble(0x7ff8000000000001L),
                Double.longBitsToDouble(0x7ff8000000000002L), Double.POSITIVE_INFINITY};
        for (double x : values) {
            int before = source.calls;
            assertBits(x * 1D, noise.noiseFastSigned2D(x, 7D));
            assertBits(x * 1D, noise.noiseFastSigned2D(x, 7D));
            assertEquals(before + 1, source.calls);
            assertBits(x * 1D, noise.noiseFastSigned3D(x, 11D, 7D));
            assertBits(x * 1D, noise.noiseFastSigned3D(x, 11D, 7D));
            assertEquals(before + 2, source.calls);
        }
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static final class CountingNoise implements NoiseGenerator {
        private int calls;

        @Override
        public double noise(double x) {
            return noise(x, 0D);
        }

        @Override
        public double noise(double x, double z) {
            return (noiseSigned(x, z) + 1D) * 0.5D;
        }

        @Override
        public double noise(double x, double y, double z) {
            return (noiseSigned(x, y, z) + 1D) * 0.5D;
        }

        @Override
        public double noiseSigned(double x, double z) {
            calls++;
            return x;
        }

        @Override
        public double noiseSigned(double x, double y, double z) {
            calls++;
            return x;
        }
    }
}
