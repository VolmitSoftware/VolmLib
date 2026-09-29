package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CNGSignedFractureMemoTest {
    @Test
    public void memoizedFractureChainsMatchPublicPathBits() {
        CNG memoized = chain(false);
        CNG reference = chain(true);
        for (int pass = 0; pass < 2; pass++) {
            for (int index = -300; index <= 300; index++) {
                double x = index * 1.37D;
                double y = index * -0.61D + 40D;
                double z = index * 0.29D - 7D;
                assertBits(reference.noiseFastSigned3D(x, y, z), memoized.noiseFastSigned3D(x, y, z));
                assertBits(reference.noiseFastSigned3D(z, x, y), memoized.noiseFastSigned3D(z, x, y));
                assertBits(reference.noiseFastSigned2D(x, z), memoized.noiseFastSigned2D(x, z));
                assertBits(reference.noiseFastSigned2D(z, x), memoized.noiseFastSigned2D(z, x));
            }
        }
    }

    @Test
    public void rotatedFractureCoordinatesAreSampledOncePerRoot() {
        AtomicInteger calls = new AtomicInteger();
        CNG root = new CNG(new RNG(3L), new CountingSimplex(3L, calls), 1D, 1);
        CNG node = root;
        for (int depth = 0; depth < 5; depth++) {
            CNG fracture = new CNG(new RNG(11L + depth), new CountingSimplex(11L + depth, calls), 1D, 1);
            node.fractureWith(fracture, 6D - depth);
            node = fracture;
        }
        root.noiseFastSigned3D(12.5D, 40.25D, -3.75D);
        assertTrue("generator calls=" + calls.get(), calls.get() < 80);
    }

    private static CNG chain(boolean publicPath) {
        CNG root = node(5L, false);
        CNG current = root;
        for (int depth = 0; depth < 4; depth++) {
            CNG fracture = node(21L + depth, publicPath);
            current.fractureWith(fracture, 8D - depth);
            current = fracture;
        }
        return root;
    }

    private static CNG node(long seed, boolean publicPath) {
        NoiseGenerator source = new SimplexNoise(seed);
        return publicPath ? new PublicPathCNG(new RNG(seed), source) : new CNG(new RNG(seed), source, 1D, 1);
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static final class PublicPathCNG extends CNG {
        private PublicPathCNG(RNG random, NoiseGenerator source) {
            super(random, source, 1D, 1);
        }
    }

    private static final class CountingSimplex extends SimplexNoise {
        private final AtomicInteger calls;

        private CountingSimplex(long seed, AtomicInteger calls) {
            super(seed);
            this.calls = calls;
        }

        @Override
        public double noiseSigned(double x, double z) {
            calls.incrementAndGet();
            return super.noiseSigned(x, z);
        }

        @Override
        public double noiseSigned(double x, double y, double z) {
            calls.incrementAndGet();
            return super.noiseSigned(x, y, z);
        }
    }
}
