package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CNGUnsignedFractureTest {
    @Test
    public void deepFracturesPreserveRawBitsAcrossSeedsTransformsAndCoordinates() {
        for (long seed = 1; seed <= 5; seed++) {
            CNG noise = CNG.signatureDouble(new RNG(seed));
            for (int transform = 0; transform < 3; transform++) {
                for (int index = 0; index < 512; index++) {
                    assertBits(noise.noise(x(index), z(index)), noise.noiseFast2D(x(index), z(index)));
                }
                noise.zoom(1.31D).pow(1.13D).up(0.03D).down(0.01D).patch(0.97D);
                noise.getFracture().oct(2);
            }
        }
    }

    @Test
    public void signatureDoubleEvaluatesOnlyNinePrimitiveSamplesPerCall() throws Exception {
        CNG noise = CNG.signatureDouble(new RNG(1337));
        AtomicInteger calls = new AtomicInteger();
        instrument(noise, calls::incrementAndGet);
        double expected = noise.noise(1.25D, -3.75D);
        assertEquals(31, calls.getAndSet(0));
        assertBits(expected, noise.noiseFast2D(1.25D, -3.75D));
        assertEquals(9, calls.getAndSet(0));
        assertBits(expected, noise.noiseFast2D(1.25D, -3.75D));
        assertEquals(9, calls.get());
        assertMemoCleared();
    }

    @Test
    public void supportedBasesAndNestedOffsetsPreserveRawBits() throws Exception {
        for (NoiseType type : new NoiseType[]{NoiseType.SIMPLEX, NoiseType.PERLIN,
                NoiseType.FRACTAL_BILLOW_SIMPLEX, NoiseType.FRACTAL_FBM_SIMPLEX}) {
            CNG noise = new CNG(new RNG(1), type, 1D, 3);
            for (int depth = 0; depth < 4; depth++) {
                NoiseGenerator source = new OffsetNoiseGenerator(type.create(depth + 1L), depth + 31L);
                noise = new CNG(new RNG(depth + 2L), source, 1D, 3).fractureWith(noise, 17.5D);
            }
            assertTrue(eligible(noise));
            for (int index = 0; index < 256; index++) {
                assertBits(noise.noise(x(index), z(index)), noise.noiseFast2D(x(index), z(index)));
            }
        }
    }

    @Test
    public void shallowFracturesBypassMemoization() throws Exception {
        memoLocal().remove();
        CNG noise = CNG.signatureHalf(new RNG(1337));
        AtomicInteger calls = new AtomicInteger();
        instrument(noise, calls::incrementAndGet);
        assertFalse(eligible(noise));
        noise.noiseFast2D(1.25D, -3.75D);
        assertEquals(15, calls.get());
        assertMemoCleared();
    }

    @Test
    public void customFracturesAndInjectorsRetainEveryInvocation() throws Exception {
        CNG serial = dynamicFracture();
        CNG fast = dynamicFracture();
        assertFalse(eligible(fast));
        for (int index = 0; index < 64; index++) {
            assertBits(serial.noise(x(index), z(index)), fast.noiseFast2D(x(index), z(index)));
        }
        CNG serialCallback = callbackFracture();
        CNG fastCallback = callbackFracture();
        assertFalse(eligible(fastCallback));
        for (int index = 0; index < 64; index++) {
            assertBits(serialCallback.noise(x(index), z(index)), fastCallback.noiseFast2D(x(index), z(index)));
        }
    }

    @Test
    public void subclassFracturesKeepTheirVirtualDispatch() throws Exception {
        CNG noise = CNG.signatureDouble(new RNG(1337));
        AtomicInteger calls = new AtomicInteger();
        leaf(noise).setFracture(new CNG(new RNG(9)) {
            @Override
            public double noiseFast2D(double x, double z) {
                return calls.incrementAndGet() * 0.0001D;
            }
        });
        assertFalse(eligible(noise));
        noise.noiseFast2D(12D, -8D);
        assertEquals(32, calls.get());
    }

    @Test
    public void mutationsAreObservedByTheNextTopLevelCall() throws Exception {
        CNG noise = CNG.signatureDouble(new RNG(1337));
        assertTrue(eligible(noise));
        noise.noiseFast2D(13.5D, -6.25D);
        noise.getFracture().zoom(2.1D).pow(1.37D);
        assertBits(noise.noise(13.5D, -6.25D), noise.noiseFast2D(13.5D, -6.25D));
        leaf(noise).setGenerator(new StatefulNoise());
        assertFalse(eligible(noise));
        leaf(noise).setGenerator(new SimplexNoise(19));
        assertTrue(eligible(noise));
        assertBits(noise.noise(13.5D, -6.25D), noise.noiseFast2D(13.5D, -6.25D));
        noise.getFracture().child(new CNG(new RNG(17)));
        assertFalse(eligible(noise));
    }

    @Test
    public void failuresClearOwnersAndDoNotPoisonTheNextCall() throws Exception {
        CNG noise = CNG.signatureDouble(new RNG(1337));
        RuntimeException expected = new IllegalStateException("sample failed");
        AtomicInteger remaining = new AtomicInteger(5);
        AtomicInteger retainedAtFailure = new AtomicInteger();
        instrument(noise, () -> {
            if (remaining.decrementAndGet() == 0) {
                try {
                    Object memo = memoLocal().get();
                    retainedAtFailure.set(declaredField(memo.getClass(), "size").getInt(memo));
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
                throw expected;
            }
        });
        try {
            noise.noiseFast2D(8D, -3D);
            fail("Expected the provider failure");
        } catch (RuntimeException failure) {
            assertSame(expected, failure);
        }
        assertTrue(retainedAtFailure.get() > 0);
        assertMemoCleared();
        assertBits(noise.noise(8D, -3D), noise.noiseFast2D(8D, -3D));
        assertMemoCleared();
    }

    @Test
    public void reentrantCallsLeaveTheOuterMemoIntact() throws Exception {
        CNG outer = CNG.signatureDouble(new RNG(1337));
        CNG inner = CNG.signatureDouble(new RNG(719));
        double innerExpected = inner.noise(5D, -7D);
        double outerExpected = outer.noise(1D, -2D);
        AtomicInteger remaining = new AtomicInteger(1);
        instrument(outer, () -> {
            if (remaining.getAndDecrement() > 0) {
                assertBits(innerExpected, inner.noiseFast2D(5D, -7D));
            }
        });
        assertBits(outerExpected, outer.noiseFast2D(1D, -2D));
        assertMemoCleared();
    }

    @Test
    public void capacityIsBoundedAndOwnersAreReleased() throws Exception {
        CNG noise = new CNG(new RNG(1));
        for (int depth = 0; depth < 36; depth++) {
            noise = new CNG(new RNG(depth + 2)).fractureWith(noise, 31D);
        }
        AtomicInteger largestSize = new AtomicInteger();
        instrument(noise, () -> {
            try {
                Object memo = memoLocal().get();
                int size = declaredField(memo.getClass(), "size").getInt(memo);
                largestSize.accumulateAndGet(size, Math::max);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
        double first = noise.noiseFast2D(2.5D, -3.5D);
        assertTrue(Double.isFinite(first));
        assertEquals(64, largestSize.get());
        assertMemoCleared();
        assertBits(first, noise.noiseFast2D(2.5D, -3.5D));
        assertMemoCleared();
    }

    @Test
    public void sharedGeneratorsPreserveRawBitsAcrossThreads() throws Exception {
        CNG noise = CNG.signatureDouble(new RNG(1337));
        long[] expected = new long[1024];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = Double.doubleToRawLongBits(noise.noise(x(index), z(index)));
        }
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                results.add(workers.submit(() -> {
                    for (int index = 0; index < expected.length; index++) {
                        assertEquals(expected[index], Double.doubleToRawLongBits(noise.noiseFast2D(x(index), z(index))));
                    }
                    try {
                        assertMemoCleared();
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                }));
            }
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        } finally {
            workers.shutdownNow();
        }
    }

    private static double x(int index) {
        return switch (index & 7) {
            case 0 -> -0D;
            case 1 -> index - 8192D;
            case 2 -> (index - 8192D) / 97D;
            case 3 -> 30_000_000D - index * 3D;
            default -> index * 0.7548776662466927D - 65_536D;
        };
    }

    private static double z(int index) {
        return (index & 7) == 0 ? +0D : index * 0.5698402909980532D - 32_768D;
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static CNG dynamicFracture() {
        CNG noise = CNG.signatureDouble(new RNG(1337));
        leaf(noise).setGenerator(new StatefulNoise());
        return noise;
    }

    private static CNG callbackFracture() {
        CNG noise = CNG.signatureDouble(new RNG(1337));
        CNG inner = noise.getFracture().getFracture();
        long[] calls = new long[1];
        inner.child(new CNG(new RNG(19)));
        inner.injectWith((a, b) -> new double[]{a + b + ++calls[0] * 0.00001D, 1D});
        return noise;
    }

    private static CNG leaf(CNG noise) {
        while (noise.getFracture() != null) {
            noise = noise.getFracture();
        }
        return noise;
    }

    private static boolean eligible(CNG noise) throws ReflectiveOperationException {
        Method method = CNG.class.getDeclaredMethod("canMemoizeUnsignedFracture");
        method.setAccessible(true);
        return (boolean) method.invoke(noise);
    }

    private static ThreadLocal<?> memoLocal() throws ReflectiveOperationException {
        return (ThreadLocal<?>) declaredField(CNG.class, "UNSIGNED_MEMO").get(null);
    }

    private static void assertMemoCleared() throws ReflectiveOperationException {
        Object memo = memoLocal().get();
        assertFalse(declaredField(memo.getClass(), "active").getBoolean(memo));
        assertEquals(0, declaredField(memo.getClass(), "size").getInt(memo));
        Object[] owners = (Object[]) declaredField(memo.getClass(), "owners").get(memo);
        for (Object owner : owners) {
            assertEquals(null, owner);
        }
    }

    private static Field declaredField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void instrument(CNG noise, Runnable onSample) throws ReflectiveOperationException {
        for (CNG node = noise; node != null; node = node.getFracture()) {
            NoiseGenerator base = node.getGenerator();
            while (base instanceof OffsetNoiseGenerator offset) {
                base = offset.getBase();
            }
            Field field = declaredField(SimplexNoise.class, "n");
            FastNoiseDouble original = (FastNoiseDouble) field.get(base);
            field.set(base, new ObservedSimplex(original, onSample));
        }
    }

    private static final class ObservedSimplex extends FastNoiseDouble {
        private final FastNoiseDouble source;
        private final Runnable onSample;

        private ObservedSimplex(FastNoiseDouble source, Runnable onSample) {
            super(0);
            this.source = source;
            this.onSample = onSample;
        }

        @Override
        public double GetSimplex(double x, double z) {
            onSample.run();
            return source.GetSimplex(x, z);
        }
    }

    private static final class StatefulNoise implements NoiseGenerator {
        private long calls;

        @Override
        public double noise(double x) {
            return noise(x, 0D);
        }

        @Override
        public double noise(double x, double z) {
            return ++calls * 0.00001D + (x - z) * 0.000001D;
        }

        @Override
        public double noise(double x, double y, double z) {
            return noise(x, z);
        }
    }
}
