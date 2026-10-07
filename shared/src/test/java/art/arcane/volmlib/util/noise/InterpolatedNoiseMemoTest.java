package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InterpolatedNoiseMemoTest {
    @Test
    public void memoizedLatticeSamplesMatchFreshSamplesForEveryMethod() {
        NoiseType[] types = {NoiseType.CLOVER, NoiseType.CELLULAR, NoiseType.WHITE, NoiseType.HEXAGON};
        for (NoiseType type : types) {
            for (InterpolationMethod method : InterpolationMethod.values()) {
                long seed = 7919L * type.ordinal() + method.ordinal();
                InterpolatedNoise noise = new InterpolatedNoise(seed, type, method);
                NoiseGenerator source = type.create(seed);
                double scale = type.getCoordinateScale();
                for (int z = -70; z <= 70; z += 3) {
                    for (int x = -70; x <= 70; x += 5) {
                        double expected = Math.max(0D, Math.min(1D, IrisInterpolation.getNoise(method, x, z, 32D,
                                (sampleX, sampleZ) -> source.noise(sampleX * scale, sampleZ * scale))));
                        assertEquals(type + "/" + method + " at " + x + "," + z, expected, noise.noise(x, z), 0D);
                    }
                }
            }
        }
    }

    @Test
    public void changingOctavesRetiresMemoizedSamples() {
        long seed = 211L;
        NoiseType type = NoiseType.SIMPLEX;
        InterpolatedNoise noise = new InterpolatedNoise(seed, type, InterpolationMethod.HERMITE);
        double scale = type.getCoordinateScale();
        for (int x = 0; x < 64; x += 4) {
            noise.noise(x, 9D);
        }
        noise.setOctaves(3);
        NoiseGenerator source = type.create(seed);
        ((OctaveNoise) source).setOctaves(3);
        for (int x = 0; x < 64; x += 4) {
            double expected = Math.max(0D, Math.min(1D, IrisInterpolation.getNoise(InterpolationMethod.HERMITE, x, 9D,
                    32D, (sampleX, sampleZ) -> source.noise(sampleX * scale, sampleZ * scale))));
            assertEquals(expected, noise.noise(x, 9D), 0D);
        }
    }
    @Test
    public void neighboringLatticeStencilsRetainAllSourcePointsWithoutCollisions() throws Exception {
        for (InterpolationMethod method : new InterpolationMethod[]{InterpolationMethod.BICUBIC,
                InterpolationMethod.HERMITE, InterpolationMethod.BILINEAR_STARCAST_6,
                InterpolationMethod.HERMITE_STARCAST_6}) {
            InterpolatedNoise noise = new InterpolatedNoise(211L, NoiseType.CLOVER, method);
            Field generator = InterpolatedNoise.class.getDeclaredField("generator");
            generator.setAccessible(true);
            CountingNoise source = new CountingNoise((NoiseGenerator) generator.get(noise));
            generator.set(noise, source);
            double expected = noise.noise(12.375D, 9.125D);
            int initialCalls = source.calls.get();
            for (int sample = 0; sample < 16; sample++) {
                assertEquals(Double.doubleToRawLongBits(expected),
                        Double.doubleToRawLongBits(noise.noise(12.375D, 9.125D)));
            }
            assertEquals(method.name(), initialCalls, source.calls.get());
        }
    }

    @Test
    public void extremeCoordinatesPreserveFreshSourceRawBits() {
        double[] coordinates = {-0D, 0D, -31.75D, 32D, 30_000_000D, -30_000_000D,
                1E18D, -1E18D, Double.MAX_VALUE, Double.MIN_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.longBitsToDouble(0x7ff8000000000001L),
                Double.longBitsToDouble(0x7ff8000000000002L)};
        for (NoiseType type : new NoiseType[]{NoiseType.CLOVER, NoiseType.WHITE}) {
            for (InterpolationMethod method : InterpolationMethod.values()) {
                InterpolatedNoise noise = new InterpolatedNoise(211L, type, method);
                NoiseGenerator source = type.create(211L);
                double scale = type.getCoordinateScale();
                for (double x : coordinates) {
                    for (double z : coordinates) {
                        double expected = Math.max(0D, Math.min(1D, IrisInterpolation.getNoise(method, x, z, 32D,
                                (sampleX, sampleZ) -> source.noise(sampleX * scale, sampleZ * scale))));
                        assertEquals(type + "/" + method + "/" + x + "/" + z,
                                Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(noise.noise(x, z)));
                    }
                }
            }
        }
    }

    @Test
    public void changingOctavesRetiresWarmMemosOnEverySamplingThread() throws Exception {
        InterpolatedNoise noise = new InterpolatedNoise(211L, NoiseType.SIMPLEX, InterpolationMethod.HERMITE);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch warmed = new CountDownLatch(4);
        CountDownLatch changed = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>(4);
        try {
            for (int thread = 0; thread < 4; thread++) {
                tasks.add(executor.submit(() -> {
                    noise.noise(12.375D, 9.125D);
                    warmed.countDown();
                    assertTrue(changed.await(5L, TimeUnit.SECONDS));
                    NoiseGenerator source = NoiseType.SIMPLEX.create(211L);
                    ((OctaveNoise) source).setOctaves(3);
                    double scale = NoiseType.SIMPLEX.getCoordinateScale();
                    for (int index = -32; index <= 32; index++) {
                        double x = index + 0.375D;
                        double expected = Math.max(0D, Math.min(1D, IrisInterpolation.getNoise(
                                InterpolationMethod.HERMITE, x, 9.125D, 32D,
                                (sampleX, sampleZ) -> source.noise(sampleX * scale, sampleZ * scale))));
                        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(noise.noise(x, 9.125D)));
                    }
                    return null;
                }));
            }
            assertTrue(warmed.await(5L, TimeUnit.SECONDS));
            noise.setOctaves(3);
            changed.countDown();
            for (Future<?> task : tasks) {
                task.get(5L, TimeUnit.SECONDS);
            }
        } finally {
            changed.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    private static final class CountingNoise implements NoiseGenerator {
        private final NoiseGenerator source;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingNoise(NoiseGenerator source) {
            this.source = source;
        }

        @Override
        public double noise(double x) {
            calls.incrementAndGet();
            return source.noise(x);
        }

        @Override
        public double noise(double x, double z) {
            calls.incrementAndGet();
            return source.noise(x, z);
        }

        @Override
        public double noise(double x, double y, double z) {
            calls.incrementAndGet();
            return source.noise(x, y, z);
        }
    }
}
