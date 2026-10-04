package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.VolmLog;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.FastNoiseDouble.FractalType;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;

public final class BillowBatch implements AutoCloseable {
    public static final int MAXIMUM_CAPACITY = 1048576;
    public static final int MINIMUM_GPU_BATCH = 65536;
    public static final int MINIMUM_RUST_BATCH = 256;

    private final Thread owner = Thread.currentThread();
    private final Arena arena;
    private final int capacity;
    private final MethodHandle gpuFill;
    private final MethodHandle gpuDestroy;
    private final MethodHandle rustBatch;
    private final long gpuHandle;
    private final String gpuUnavailable;
    private final String rustUnavailable;
    private final MemorySegment nativeInput;
    private final MemorySegment nativeOutput;
    private final long createNanos;
    private FastNoiseDouble javaGenerator;
    private long wrapperSeed;
    private long rawSeed;
    private int octaves;
    private double bounding;
    private boolean closed;
    private Backend lastBackend = Backend.JAVA;
    private String lastReason = "not-filled";
    private final long[] calls = new long[Backend.values().length];
    private final long[] samples = new long[Backend.values().length];
    private final Map<String, Long> reasons = new HashMap<>();

    public BillowBatch(Options options) {
        long start = System.nanoTime();
        Objects.requireNonNull(options);
        if (options.capacity() < 1 || options.capacity() > MAXIMUM_CAPACITY) {
            throw new IllegalArgumentException("Batch capacity must be between 1 and 1048576");
        }
        capacity = options.capacity();
        arena = Arena.ofConfined();
        GpuSetup gpu = null;
        try {
            RustSetup rust = initializeRust(options);
            gpu = initializeGpu(options, rust);
            gpuFill = gpu.fill();
            gpuDestroy = gpu.destroy();
            gpuHandle = gpu.handle();
            gpuUnavailable = gpu.reason();
            rustBatch = options.rustEnabled() ? rust.fill() : null;
            rustUnavailable = options.rustEnabled() ? rust.reason() : "rust-disabled";
            boolean nativeAvailable = gpuHandle != 0 || rustBatch != null;
            nativeInput = nativeAvailable ? arena.allocate(capacity * 24L, 8) : MemorySegment.NULL;
            nativeOutput = nativeAvailable ? arena.allocate(capacity * 8L, 8) : MemorySegment.NULL;
            createNanos = System.nanoTime() - start;
        } catch (RuntimeException | Error failure) {
            if (gpu != null && gpu.handle() != 0) {
                try {
                    gpu.destroy().invokeExact(gpu.handle());
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            try {
                arena.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public Backend fill(Request request) {
        requireOpen();
        Objects.requireNonNull(request);
        double[] xyz = Objects.requireNonNull(request.xyz());
        double[] output = Objects.requireNonNull(request.output());
        int count = request.count();
        if (count < 0 || count > capacity || xyz.length < count * 3L || output.length < count) {
            throw new IllegalArgumentException("Batch arrays or count exceed the configured capacity");
        }
        prepareGenerator(request.seed(), request.octaves());
        boolean gpuEligible = gpuHandle != 0 && count >= MINIMUM_GPU_BATCH && (octaves == 8 || octaves == 9);
        boolean rustEligible = rustBatch != null && count >= MINIMUM_RUST_BATCH && (octaves == 8 || octaves == 9);
        String reason = gpuHandle == 0 ? gpuUnavailable : count < MINIMUM_GPU_BATCH ? "gpu-small-batch"
                : octaves != 8 && octaves != 9 ? "gpu-unsupported-octaves" : "gpu-fill-declined";
        if (gpuEligible || rustEligible) {
            MemorySegment.copy(MemorySegment.ofArray(xyz), 0, nativeInput, 0, count * 24L);
        }
        if (gpuEligible) {
            int status = invokeGpu(count);
            if (status == 1) {
                MemorySegment.copy(nativeOutput, 0, MemorySegment.ofArray(output), 0, count * 8L);
                lastBackend = Backend.GPU;
                lastReason = "gpu-eligible";
                return record(count);
            }
            if (status != 0) {
                throw new IllegalStateException("Unexpected GPU fill status: " + status);
            }
        }
        if (rustEligible) {
            invokeRust(count);
            MemorySegment.copy(nativeOutput, 0, MemorySegment.ofArray(output), 0, count * 8L);
            lastBackend = Backend.RUST;
            lastReason = reason;
            return record(count);
        }
        for (int index = 0; index < count; index++) {
            output[index] = (javaGenerator.GetSimplexFractal(xyz[index * 3], xyz[index * 3 + 2]) / 2D) + 0.5D;
        }
        lastBackend = Backend.JAVA;
        String rustReason = rustBatch == null ? rustUnavailable : count < MINIMUM_RUST_BATCH ? "rust-small-batch" : "rust-unsupported-octaves";
        lastReason = reason + ";" + rustReason;
        return record(count);
    }

    public int capacity() { return capacity; }

    public Statistics statistics() {
        requireOwner();
        return new Statistics(calls[0], samples[0], calls[1], samples[1], calls[2], samples[2], Map.copyOf(reasons));
    }

    private Backend record(int count) {
        calls[lastBackend.ordinal()]++;
        samples[lastBackend.ordinal()] += count;
        reasons.merge(lastReason, 1L, Long::sum);
        return lastBackend;
    }

    public record Statistics(long gpuCalls, long gpuSamples, long rustCalls, long rustSamples, long javaCalls, long javaSamples, Map<String, Long> reasons) {}

    public Selection selection() {
        requireOwner();
        return new Selection(lastBackend, lastReason);
    }

    public long createNanos() {
        return createNanos;
    }

    @Override
    public void close() {
        requireOwner();
        if (closed) {
            return;
        }
        try {
            if (gpuHandle != 0) {
                gpuDestroy.invokeExact(gpuHandle);
            }
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("GPU context destruction failed", failure);
        } finally {
            closed = true;
            arena.close();
        }
    }

    private GpuSetup initializeGpu(Options options, RustSetup validation) {
        if (!options.gpuEnabled() || capacity < MINIMUM_GPU_BATCH) {
            return new GpuSetup(0, null, null, "gpu-disabled");
        }
        if (!BillowBatch.class.getModule().isNativeAccessEnabled()) {
            return new GpuSetup(0, null, null, "gpu-native-access-disabled");
        }
        if (options.library() == null || !Files.isRegularFile(options.library())) {
            return new GpuSetup(0, null, null, "gpu-library-missing");
        }
        if (validation.fill() == null) {
            return new GpuSetup(0, null, null, "gpu-cpu-validation-unavailable:" + validation.reason());
        }
        try {
            SymbolLookup library = SymbolLookup.libraryLookup(options.library().toAbsolutePath(), arena);
            Linker linker = Linker.nativeLinker();
            MethodHandle create = linker.downcallHandle(library.find("iris_gpu_create").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG));
            MethodHandle fill = linker.downcallHandle(library.find("iris_gpu_fill").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            MethodHandle destroy = linker.downcallHandle(library.find("iris_gpu_destroy").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
            long handle = (long) create.invokeExact();
            return new GpuSetup(handle, fill, destroy, handle == 0 ? "gpu-create-declined" : "gpu-ready");
        } catch (RuntimeException | LinkageError failure) {
            VolmLog.warning("NoiseBatch", "GPU initialization failed; using CPU", failure);
            return new GpuSetup(0, null, null, "gpu-initialization-failed");
        } catch (Throwable failure) {
            throw new IllegalStateException("GPU initialization failed", failure);
        }
    }

    private RustSetup initializeRust(Options options) {
        if (!options.rustEnabled() && (!options.gpuEnabled() || capacity < MINIMUM_GPU_BATCH)) {
            return new RustSetup(null, "rust-disabled");
        }
        if (!BillowBatch.class.getModule().isNativeAccessEnabled()) {
            return new RustSetup(null, "rust-native-access-disabled");
        }
        if (options.library() == null || !Files.isRegularFile(options.library())) {
            return new RustSetup(null, "rust-library-missing");
        }
        try {
            SymbolLookup library = SymbolLookup.libraryLookup(options.library().toAbsolutePath(), arena);
            MethodHandle fill = Linker.nativeLinker().downcallHandle(library.find("billow_batch").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE));
            validateRust(fill);
            return new RustSetup(fill, "rust-ready");
        } catch (RuntimeException | LinkageError failure) {
            VolmLog.warning("NoiseBatch", "Rust initialization failed; using Java", failure);
            return new RustSetup(null, "rust-initialization-failed");
        } catch (Throwable failure) {
            throw new IllegalStateException("Rust initialization failed", failure);
        }
    }

    private void validateRust(MethodHandle fill) throws Throwable {
        double[] xyz = {0D, 0D, -0D, -100D, 0D, -300D, -9017.125D, 0D, 12546.875D, 29_999_900.125D, 0D, -29_999_900.625D};
        MemorySegment input = arena.allocate(xyz.length * 8L, 8);
        MemorySegment output = arena.allocate(32L, 8);
        MemorySegment.copy(MemorySegment.ofArray(xyz), 0, input, 0, input.byteSize());
        for (long seed : new long[]{0, -1, 1337}) {
            FastNoiseDouble reference = new FastNoiseDouble(new RNG(seed).lmax());
            reference.setFractalType(FractalType.Billow);
            long kernelSeed = new RNG(seed).lmax();
            for (int configuredOctaves : new int[]{1, 4, 8, 9}) {
                reference.setFractalOctaves(configuredOctaves);
                double bound = octaveBounding(configuredOctaves);
                fill.invokeExact(kernelSeed, input, output, 4, 2, configuredOctaves, bound);
                for (int index = 0; index < 4; index++) {
                    double expected = (reference.GetSimplexFractal(xyz[index * 3], xyz[index * 3 + 2]) / 2D) + 0.5D;
                    double actual = output.getAtIndex(ValueLayout.JAVA_DOUBLE, index);
                    if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
                        throw new IllegalStateException("Rust batch self-test differs at seed=" + seed + " octaves=" + configuredOctaves);
                    }
                }
            }
        }
    }

    private void prepareGenerator(long seed, int configuredOctaves) {
        boolean changedSeed = javaGenerator == null || wrapperSeed != seed;
        if (changedSeed) {
            wrapperSeed = seed;
            rawSeed = new RNG(seed).lmax();
            javaGenerator = new FastNoiseDouble(rawSeed);
            javaGenerator.setFractalType(FractalType.Billow);
        }
        if (changedSeed || octaves != configuredOctaves) {
            octaves = configuredOctaves;
            javaGenerator.setFractalOctaves(octaves);
            bounding = octaveBounding(octaves);
        }
    }

    private static double octaveBounding(int configuredOctaves) {
        double amplitude = 0.5D;
        double sum = 1D;
        for (int octave = 1; octave < configuredOctaves; octave++) {
            sum += amplitude;
            amplitude *= 0.5D;
        }
        return 1D / sum;
    }

    private int invokeGpu(int count) {
        try {
            return (int) gpuFill.invokeExact(gpuHandle, rawSeed, octaves, nativeInput, nativeOutput, count);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("GPU batch sampling failed", failure);
        }
    }

    private void invokeRust(int count) {
        try {
            rustBatch.invokeExact(rawSeed, nativeInput, nativeOutput, count, 2, octaves, bounding);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Rust batch sampling failed", failure);
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Batch context belongs to another thread");
        }
    }

    private void requireOpen() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("Batch context is closed");
        }
    }

    public enum Backend {
        GPU,
        RUST,
        JAVA
    }

    public record Options(boolean gpuEnabled, boolean rustEnabled, Path library, int capacity) {
    }

    public record Request(long seed, int octaves, double[] xyz, double[] output, int count) {
    }

    public record Selection(Backend backend, String reason) {
    }

    private record GpuSetup(long handle, MethodHandle fill, MethodHandle destroy, String reason) {
    }

    private record RustSetup(MethodHandle fill, String reason) {
    }
}
