package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.FastNoiseDouble.FractalType;

import org.junit.Test;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BillowBatchTest {
    @Test
    public void javaMatchesReferenceAndCounts() {
        try (BillowBatch batch = new BillowBatch(new BillowBatch.Options(false, false, null, 256))) {
            double[] xyz = coordinates(256);
            double[] output = new double[256];
            for (long seed : new long[]{0L, -1L, Long.MAX_VALUE}) {
                for (int octaves : new int[]{1, 4, 8, 9}) {
                    assertEquals(BillowBatch.Backend.JAVA, batch.fill(new BillowBatch.Request(seed, octaves, xyz, output, 256)));
                    compare(seed, octaves, xyz, output);
                }
            }
            assertEquals(12L, batch.statistics().javaCalls());
            assertEquals(3072L, batch.statistics().javaSamples());
        }
    }
    @Test
    public void javaBatchRemainsJavaWithScalarNativeEnabled() {
        if (!Boolean.getBoolean("volmlib.test.scalarBillowCoexistence")) {
            return;
        }
        assertEquals("native", FractalBillowSimplexNoise.nativeBackendStatus());
        FractalBillowSimplexNoise scalar = new FractalBillowSimplexNoise(1337L);
        scalar.setOctaves(8);
        double[] xyz = coordinates(256);
        double[] output = new double[256];
        try (BillowBatch batch = new BillowBatch(new BillowBatch.Options(false, false, null, 256))) {
            assertEquals(BillowBatch.Backend.JAVA, batch.fill(new BillowBatch.Request(1337L, 8, xyz, output, 256)));
            compare(1337L, 8, xyz, output);
            for (int index = 0; index < output.length; index++) {
                assertEquals(Double.doubleToRawLongBits(scalar.noise(xyz[index * 3], xyz[index * 3 + 2])),
                        Double.doubleToRawLongBits(output[index]));
            }
        }
    }

    @Test
    public void missingLibraryFallsBack() {
        try (BillowBatch batch = new BillowBatch(new BillowBatch.Options(true, true, Path.of("does-not-exist"), 65536))) {
            assertEquals(BillowBatch.Backend.JAVA, batch.fill(new BillowBatch.Request(0L, 8, coordinates(256), new double[256], 256)));
            assertTrue(batch.selection().reason().contains("library-missing"));
        }
    }
    @Test
    public void ownerAndCloseAreEnforced() throws InterruptedException {
        BillowBatch batch = new BillowBatch(new BillowBatch.Options(false, false, null, 1));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> { try { batch.close(); } catch (Throwable caught) { failure.set(caught); } });
        thread.start(); thread.join();
        assertTrue(failure.get() instanceof IllegalStateException);
        batch.close(); batch.close();
        assertThrows(IllegalStateException.class, () -> batch.fill(new BillowBatch.Request(0, 8, new double[3], new double[1], 1)));
    }
    @Test
    public void rejectsOversizedArraysBeforeSampling() {
        try (BillowBatch batch = new BillowBatch(new BillowBatch.Options(false, false, null, 2))) {
            assertThrows(IllegalArgumentException.class, () -> batch.fill(new BillowBatch.Request(0, 8, new double[3], new double[1], 2)));
            assertThrows(IllegalArgumentException.class, () -> batch.fill(new BillowBatch.Request(0, 8, new double[9], new double[3], 3)));
        }
    }
    @Test
    public void nativeCpuMatchesWhenConfigured() {
        String library = System.getProperty("volmlib.billow.library");
        org.junit.Assume.assumeNotNull(library);
        try (BillowBatch batch = new BillowBatch(new BillowBatch.Options(false, true, Path.of(library), 256))) {
            double[] xyz = coordinates(256); double[] output = new double[256];
            for (long seed : new long[]{0L, -1L, Long.MAX_VALUE}) {
                for (int octaves : new int[]{8, 9}) {
                    assertEquals(BillowBatch.Backend.RUST, batch.fill(new BillowBatch.Request(seed, octaves, xyz, output, 256)));
                    compare(seed, octaves, xyz, output);
                }
            }
        }
    }
    @Test
    public void gpuOnlyStillValidatesCpuAndKeepsRustDisabled() {
        String library = System.getProperty("volmlib.billow.library");
        org.junit.Assume.assumeNotNull(library);
        int count = BillowBatch.MINIMUM_GPU_BATCH;
        try (BillowBatch batch = new BillowBatch(new BillowBatch.Options(true, false, Path.of(library), count))) {
            double[] xyz = coordinates(count);
            double[] output = new double[count];
            BillowBatch.Backend backend = batch.fill(new BillowBatch.Request(1337L, 8, xyz, output, count));
            assertEquals(0L, batch.statistics().rustCalls());
            org.junit.Assume.assumeTrue(backend == BillowBatch.Backend.GPU);
            assertEquals(1L, batch.statistics().gpuCalls());
            FastNoiseDouble reference = new FastNoiseDouble(new RNG(1337L).lmax());
            reference.setFractalType(FractalType.Billow);
            reference.setFractalOctaves(8);
            for (int index = 0; index < count; index++) {
                double expected = ((reference.GetSimplexFractal(xyz[index * 3], xyz[index * 3 + 2]) / 2D) + 0.5D);
                double delta = Math.abs(output[index] - expected);
                assertTrue(delta <= Math.abs(expected) * 0.05D);
                assertTrue(delta * 2D <= Math.abs(expected * 2D - 1D) * 0.05D);
            }
        }
    }
    private static double[] coordinates(int count) {
        double[] xyz = new double[count * 3];
        for (int index = 0; index < count; index++) { xyz[index * 3] = index * 131.125 - 16000.; xyz[index * 3 + 2] = index * -67.625 + 1000.; }
        return xyz;
    }
    private static void compare(long seed, int octaves, double[] xyz, double[] output) {
        FastNoiseDouble reference = new FastNoiseDouble(new RNG(seed).lmax()); reference.setFractalType(FractalType.Billow); reference.setFractalOctaves(octaves);
        for (int index = 0; index < output.length; index++) { assertEquals(Double.doubleToRawLongBits(((reference.GetSimplexFractal(xyz[index * 3], xyz[index * 3 + 2]) / 2D) + 0.5D)), Double.doubleToRawLongBits(output[index])); }
    }
}
