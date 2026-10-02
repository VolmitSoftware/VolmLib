package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CNGConcurrentSamplingTest {
    private static final int WORKERS = 6;
    private static final int GENERATORS = 8;
    private static final int COORDINATES = 8;

    @Test(timeout = 60000L)
    public void coldAndRepeatedConcurrentSamplesMatchSerialReferences() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        try {
            for (int round = 0; round < 64; round++) {
                CNG[] shared = new CNG[GENERATORS];
                double[][][] expected = new double[GENERATORS][COORDINATES][];
                for (int index = 0; index < GENERATORS; index++) {
                    long seed = 0xB72947DL + round * 65537L + index;
                    shared[index] = create(seed, index);
                    CNG reference = create(seed, index);
                    for (int coordinate = 0; coordinate < COORDINATES; coordinate++) {
                        expected[index][coordinate] = sample(reference, coordinate);
                    }
                }

                CountDownLatch ready = new CountDownLatch(WORKERS);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>(WORKERS);
                for (int worker = 0; worker < WORKERS; worker++) {
                    int order = worker;
                    futures.add(executor.submit(() -> checkSamples(shared, expected, order, ready, start)));
                }
                try {
                    assertTrue("Sampling workers did not become ready", ready.await(10L, TimeUnit.SECONDS));
                } finally {
                    start.countDown();
                }
                for (Future<?> future : futures) {
                    future.get(20L, TimeUnit.SECONDS);
                }
            }
        } finally {
            executor.shutdownNow();
            assertTrue("Sampling workers did not stop", executor.awaitTermination(10L, TimeUnit.SECONDS));
        }
    }

    private static void checkSamples(CNG[] shared, double[][][] expected, int order,
                                     CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10L, TimeUnit.SECONDS)) {
                throw new AssertionError("Sampling workers did not start");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }

        for (int index = 0; index < GENERATORS; index++) {
            assertSample(expected[index][0], sample(shared[index], 0));
        }
        for (int pass = 0; pass < 3; pass++) {
            for (int index = 0; index < GENERATORS; index++) {
                int generator = (index * 3 + order) % GENERATORS;
                for (int coordinate = 0; coordinate < COORDINATES; coordinate++) {
                    int position = (coordinate * 5 + order + pass) % COORDINATES;
                    assertSample(expected[generator][position], sample(shared[generator], position));
                }
            }
        }
    }

    private static CNG create(long seed, int variant) {
        NoiseType type = switch (variant % 4) {
            case 0 -> NoiseType.SIMPLEX;
            case 1 -> NoiseType.PERLIN;
            case 2 -> NoiseType.FRACTAL_FBM_SIMPLEX;
            default -> NoiseType.CLOVER;
        };
        CNG generator = new CNG(new RNG(seed), type, variant % 2 == 0 ? 1D : 0.73D, 3)
                .scale(0.03125D).bake().scale(0.75D);
        if (variant >= 4) {
            generator.fractureWith(CNG.signature(new RNG(seed + 811L)).zoom(2D), 7.25D);
        }
        if (variant == 3 || variant == 7) {
            generator.up(0.125D).down(0.03125D).patch(0.875D).pow(1.25D);
        }
        return generator;
    }

    private static double[] sample(CNG generator, int coordinate) {
        double x = -129.25D + coordinate * 37.5D;
        double y = -63.75D + coordinate * 11.25D;
        double z = 93.5D - coordinate * 41.75D;
        return new double[]{generator.noiseFastSigned2D(x, z), generator.noiseFastSigned3D(x, y, z),
                generator.noiseFast2D(x, z), generator.noiseFast3D(x, y, z)};
    }

    private static void assertSample(double[] expected, double[] actual) {
        for (int mode = 0; mode < expected.length; mode++) {
            assertEquals("Sample mode " + mode, Double.doubleToRawLongBits(expected[mode]),
                    Double.doubleToRawLongBits(actual[mode]));
        }
    }
}
