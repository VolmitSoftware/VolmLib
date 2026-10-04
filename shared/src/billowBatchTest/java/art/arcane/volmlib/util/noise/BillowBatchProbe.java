package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.FastNoiseDouble.FractalType;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;

public final class BillowBatchProbe {
    private static final int COUNT = Integer.getInteger("volmlib.billow.probe.count", 65536);
    private static volatile double sink;

    public static void main(String[] args) throws Exception {
        Path library = Path.of(args[1]).toAbsolutePath();
        System.out.println("reference_classes=" + FastNoiseDouble.class.getProtectionDomain().getCodeSource().getLocation());
        if (args[0].equals("correctness")) {
            correctness(library);
        } else if (args[0].equals("noaccess")) {
            double[] xyz = coordinates(COUNT);
            verifyCase("native-access-disabled", options(library, true, true, COUNT), xyz, new double[COUNT], new TestRequest(1337, 8, COUNT, BillowBatch.Backend.JAVA));
        } else if (args[0].equals("timing")) {
            timing(library, args.length > 2 ? Integer.parseInt(args[2]) : 0);
        } else {
            throw new IllegalArgumentException("Expected correctness, noaccess, or timing");
        }
    }

    private static void correctness(Path library) throws Exception {
        double[] xyz = coordinates(COUNT);
        double[] output = new double[COUNT];
        verifySeeds("gpu", options(library, true, true, COUNT), xyz, output, BillowBatch.Backend.GPU, false);
        verifySeeds("rust-gpu-disabled", options(library, false, true, COUNT), xyz, output, BillowBatch.Backend.RUST, true);
        verifyCase("gpu-small-batch-rust", options(library, true, true, COUNT), xyz, output, new TestRequest(1337, 8, 1024, BillowBatch.Backend.RUST));
        verifyCase("tiny-batch-java", options(library, true, true, COUNT), xyz, output, new TestRequest(1337, 8, 16, BillowBatch.Backend.JAVA));
        verifyCase("unsupported-octaves-java", options(library, true, true, COUNT), xyz, output, new TestRequest(1337, 4, COUNT, BillowBatch.Backend.JAVA));
        verifyCase("minimum-integer-octaves-java", options(library, true, true, COUNT), xyz, output, new TestRequest(1337, Integer.MIN_VALUE, 16, BillowBatch.Backend.JAVA));
        BillowBatch.Options absentLibraries = new BillowBatch.Options(true, true, Path.of("missing-native-library"), COUNT);
        verifyCase("both-libraries-missing", absentLibraries, xyz, output, new TestRequest(1337, 8, COUNT, BillowBatch.Backend.JAVA));
        verifyCase("both-disabled", options(library, false, false, COUNT), xyz, output, new TestRequest(1337, 9, COUNT, BillowBatch.Backend.JAVA));
        verifyLifecycle(library, xyz, output);
    }

    private static void verifySeeds(String name, BillowBatch.Options options, double[] xyz, double[] output,
                                    BillowBatch.Backend expected, boolean exact) {
        Metrics metrics = new Metrics();
        try (BillowBatch context = new BillowBatch(options)) {
            for (long seed : new long[]{0, -1, 1337, 0x9e3779b97f4a7c15L, Long.MIN_VALUE, Long.MAX_VALUE}) {
                for (int octaves : new int[]{8, 9}) {
                    BillowBatch.Backend actual = context.fill(new BillowBatch.Request(seed, octaves, xyz, output, COUNT));
                    if (actual != expected) {
                        throw new AssertionError(name + " selected " + context.selection());
                    }
                    compare(new Comparison(seed, octaves, xyz, output, COUNT, exact), metrics);
                }
            }
            print(name, context, metrics);
        }
    }

    private static void verifyCase(String name, BillowBatch.Options options, double[] xyz, double[] output, TestRequest request) {
        Metrics metrics = new Metrics();
        try (BillowBatch context = new BillowBatch(options)) {
            BillowBatch.Backend selected = context.fill(new BillowBatch.Request(request.seed(), request.octaves(), xyz, output, request.count()));
            if (selected != request.expected()) {
                throw new AssertionError(name + " selected " + context.selection());
            }
            compare(new Comparison(request.seed(), request.octaves(), xyz, output, request.count(), true), metrics);
            print(name, context, metrics);
        }
    }

    private static void compare(Comparison comparison, Metrics metrics) {
        FastNoiseDouble reference = new FastNoiseDouble(new RNG(comparison.seed()).lmax());
        reference.setFractalType(FractalType.Billow);
        reference.setFractalOctaves(comparison.octaves());
        for (int index = 0; index < comparison.count(); index++) {
            double x = comparison.xyz()[index * 3];
            double z = comparison.xyz()[index * 3 + 2];
            double expected = (reference.GetSimplexFractal(x, z) / 2D) + 0.5D;
            double actual = comparison.output()[index];
            double expectedSigned = expected * 2D - 1D;
            double actualSigned = actual * 2D - 1D;
            if (!Double.isFinite(actual) || !Double.isFinite(actualSigned)) {
                throw new AssertionError("Batch produced non-finite output");
            }
            boolean differs = Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual);
            boolean signedDiffers = Double.doubleToRawLongBits(expectedSigned) != Double.doubleToRawLongBits(actualSigned);
            if (comparison.exact() && (differs || signedDiffers)) {
                throw new AssertionError("CPU fallback differs at seed=" + comparison.seed() + " sample=" + index);
            }
            double unsignedError = Math.abs(expected - actual);
            double signedError = Math.abs(expectedSigned - actualSigned);
            double unsignedRelative = expected == 0D ? unsignedError == 0D ? 0D : Double.POSITIVE_INFINITY : unsignedError / Math.abs(expected);
            double signedRelative = expectedSigned == 0D ? signedError == 0D ? 0D : Double.POSITIVE_INFINITY : signedError / Math.abs(expectedSigned);
            if (unsignedRelative > 0.05D || signedRelative > 0.05D) {
                throw new AssertionError("GPU exceeds 5% at seed=" + comparison.seed() + " sample=" + index
                        + " unsigned=" + unsignedRelative + " signed=" + signedRelative);
            }
            metrics.points++;
            metrics.rawMismatches += differs ? 1 : 0;
            metrics.maxUnsignedRelative = Math.max(metrics.maxUnsignedRelative, unsignedRelative);
            metrics.maxSignedRelative = Math.max(metrics.maxSignedRelative, signedRelative);
            metrics.maxAbsolute = Math.max(metrics.maxAbsolute, unsignedError);
        }
    }

    private static void print(String name, BillowBatch context, Metrics metrics) {
        BillowBatch.Selection selection = context.selection();
        System.out.printf("CASE name=%s backend=%s reason=%s points=%d raw_mismatches=%d max_unsigned_rel=%.12g max_signed_rel=%.12g max_abs=%.12g create_ms=%.3f%n",
                name, selection.backend(), selection.reason(), metrics.points, metrics.rawMismatches,
                metrics.maxUnsignedRelative, metrics.maxSignedRelative, metrics.maxAbsolute, context.createNanos() / 1e6);
    }

    private static void verifyLifecycle(Path library, double[] xyz, double[] output) throws Exception {
        BillowBatch context = new BillowBatch(options(library, true, true, COUNT));
        BillowBatch.Request request = new BillowBatch.Request(1337, 8, xyz, output, COUNT);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread foreignThread = new Thread(() -> checkForeignThread(context, request, failure));
        foreignThread.start();
        foreignThread.join();
        if (failure.get() != null) {
            throw new AssertionError("Thread ownership failed", failure.get());
        }
        context.close();
        context.close();
        try {
            context.fill(request);
            throw new AssertionError("Closed context accepted fill");
        } catch (IllegalStateException expected) {
            System.out.println("LIFECYCLE wrong_thread_rejected=true after_close_rejected=true close_idempotent=true");
        }
    }

    private static void checkForeignThread(BillowBatch context, BillowBatch.Request request, AtomicReference<Throwable> failure) {
        try {
            context.fill(request);
            failure.set(new AssertionError("Foreign thread accepted fill"));
        } catch (IllegalStateException expected) {
            return;
        } catch (Throwable unexpected) {
            failure.set(unexpected);
        }
    }

    private static void timing(Path library, int rotation) {
        int capacity = 1048576;
        double[] xyz = coordinates(capacity);
        double[] output = new double[capacity];
        BillowBatch.Options[] options = {options(library, true, true, capacity), options(library, false, true, capacity), options(library, false, false, capacity)};
        BillowBatch.Backend[] expected = {BillowBatch.Backend.GPU, BillowBatch.Backend.RUST, BillowBatch.Backend.JAVA};
        for (int position = 0; position < options.length; position++) {
            int mode = Math.floorMod(position + rotation, options.length);
            try (BillowBatch context = new BillowBatch(options[mode])) {
                for (int count : new int[]{256, 65536, 1048576}) {
                    for (int octaves : new int[]{8, 9}) {
                        BillowBatch.Request request = new BillowBatch.Request(1337, octaves, xyz, output, count);
                        BillowBatch.Backend selected = count < BillowBatch.MINIMUM_GPU_BATCH && mode == 0 ? BillowBatch.Backend.RUST : expected[mode];
                        long coldStart = System.nanoTime();
                        checkFill(context, request, selected);
                        long firstFillNanos = System.nanoTime() - coldStart;
                        long warmEnd = System.nanoTime() + 200_000_000L;
                        do { checkFill(context, request, selected); } while (System.nanoTime() < warmEnd);
                        double[] timings = new double[5];
                        for (int sample = 0; sample < timings.length; sample++) {
                            long start = System.nanoTime();
                            int loops = 0;
                            long elapsed;
                            do {
                                checkFill(context, request, selected);
                                sink = output[count / 2];
                                loops++;
                                elapsed = System.nanoTime() - start;
                            } while (elapsed < 70_000_000L);
                            timings[sample] = elapsed / (double) (loops * (long) count);
                        }
                        Arrays.sort(timings);
                        System.out.printf("TIMING backend=%s count=%d octaves=%d ns=%.3f create_ms=%.3f first_fill_ms=%.3f%n",
                                selected, count, octaves, timings[2], context.createNanos() / 1e6, firstFillNanos / 1e6);
                        Metrics metrics = new Metrics();
                        compare(new Comparison(1337, octaves, xyz, output, count, selected != BillowBatch.Backend.GPU), metrics);
                        print("timing-output-" + count + "-" + octaves, context, metrics);
                    }
                }
            }
        }
        System.out.println("sink=" + sink);
    }

    private static void checkFill(BillowBatch context, BillowBatch.Request request, BillowBatch.Backend expected) {
        if (context.fill(request) != expected) {
            throw new AssertionError("Timing selected " + context.selection());
        }
    }

    private static BillowBatch.Options options(Path library, boolean gpu, boolean rust, int capacity) {
        return new BillowBatch.Options(gpu, rust, library, capacity);
    }

    private static double[] coordinates(int count) {
        double[] xyz = new double[count * 3];
        SplittableRandom random = new SplittableRandom(654321);
        for (int index = 0; index < count; index++) {
            xyz[index * 3] = random.nextDouble(-30_000_000, 30_000_000);
            xyz[index * 3 + 1] = random.nextDouble(-256, 512);
            xyz[index * 3 + 2] = random.nextDouble(-30_000_000, 30_000_000);
        }
        xyz[0] = 0D;
        xyz[1] = -0D;
        xyz[2] = 0D;
        xyz[3] = -100D;
        xyz[4] = -200D;
        xyz[5] = -300D;
        return xyz;
    }

    private record TestRequest(long seed, int octaves, int count, BillowBatch.Backend expected) {
    }

    private record Comparison(long seed, int octaves, double[] xyz, double[] output, int count, boolean exact) {
    }

    private static final class Metrics {
        private long points;
        private long rawMismatches;
        private double maxUnsignedRelative;
        private double maxSignedRelative;
        private double maxAbsolute;
    }
}
