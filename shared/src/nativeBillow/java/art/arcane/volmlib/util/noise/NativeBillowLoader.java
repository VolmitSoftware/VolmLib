package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.FastNoiseDouble.FractalType;
import art.arcane.volmlib.util.VolmLog;
import java.io.InputStream;
import java.io.IOException;
import art.arcane.volmlib.util.noise.FractalBillowSimplexNoise.NativeBackend;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class NativeBillowLoader {
    private static final Arena LIBRARY_ARENA = Arena.ofAuto();

    public static NativeBackend load() {
        if (!NativeBillowLoader.class.getModule().isNativeAccessEnabled()) {
            return new NativeBackend(null, "java-native-access-disabled");
        }
        String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String platform = operatingSystem.contains("mac") ? "macos" : operatingSystem.contains("linux") ? "linux" : "unsupported";
        String architecture = switch (System.getProperty("os.arch")) {
            case "aarch64", "arm64" -> "aarch64";
            case "amd64", "x86_64" -> "x86_64";
            default -> "unsupported";
        };
        if (platform.equals("unsupported") || architecture.equals("unsupported")) {
            return new NativeBackend(null, "java-unsupported-platform");
        }
        try {
            String configured = System.getProperty("volmlib.noise.nativeBillowLibrary");
            Path libraryPath;
            if (configured != null) {
                libraryPath = Path.of(configured);
                if (!libraryPath.isAbsolute()) {
                    return new NativeBackend(null, "java-library-path-not-absolute");
                }
                if (!Files.isRegularFile(libraryPath)) {
                    return new NativeBackend(null, "java-library-missing");
                }
            } else {
                String name = platform.equals("macos") ? "libvolmlib-billow.dylib" : "libvolmlib-billow.so";
                String resource = "/art/arcane/volmlib/native-billow/" + platform + "-" + architecture + "/" + name;
                try (InputStream stream = NativeBillowLoader.class.getResourceAsStream(resource)) {
                    if (stream == null) {
                        return new NativeBackend(null, "java-library-not-packaged");
                    }
                    libraryPath = Files.createTempFile("volmlib-billow-", platform.equals("macos") ? ".dylib" : ".so");
                    libraryPath.toFile().deleteOnExit();
                    Files.copy(stream, libraryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            SymbolLookup library = SymbolLookup.libraryLookup(libraryPath, LIBRARY_ARENA);
            FunctionDescriptor descriptor = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE);
            MethodHandle sample = Linker.nativeLinker().downcallHandle(library.find("volmlib_billow2d_v1").orElseThrow(), descriptor);
            validate(sample);
            return new NativeBackend(sample, "native");
        } catch (IOException | RuntimeException | LinkageError failure) {
            VolmLog.warning("Noise", "Native billow initialization failed; using Java", failure);
            return new NativeBackend(null, "java-initialization-failed");
        }
    }

    private static void validate(MethodHandle sample) {
        double[] edges = {0D, -0D, 1D, -1D, 100D, -100D, Math.nextDown(100D), Math.nextUp(-100D),
                30_000_000D, -30_000_000D, Double.MIN_VALUE, -Double.MIN_VALUE, 0.5D, -0.5D, 12345.5D, -12345.5D};
        for (long seed : new long[]{0, -1, 1337, 0x9e3779b97f4a7c15L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            FastNoiseDouble reference = new FastNoiseDouble(new RNG(seed).lmax());
            reference.setFractalType(FractalType.Billow);
            for (int octaves : new int[]{8, 9}) {
                reference.setFractalOctaves(octaves);
                double bounding = 1D / (2D - Math.scalb(1D, 1 - octaves));
                for (int index = 0; index < 96; index++) {
                    double x = index < 32 ? edges[index % edges.length] : (index - 48) * 73.125D;
                    double z = index < 32 ? edges[(index * 7 + 3) % edges.length] : (index * index - 512) * 0.875D;
                    if (index >= 32 && (index & 7) == 0) {
                        x += 29_999_900D;
                        z -= 29_999_900D;
                    }
                    double expected = (reference.GetSimplexFractal(x, z) / 2D) + 0.5D;
                    double actual = invoke(sample, reference.getSeed(), x, z, octaves, bounding);
                    if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
                        throw new IllegalStateException("Native billow self-test differs at seed=" + seed + " octaves=" + octaves + " sample=" + index);
                    }
                }
            }
        }
    }

    private static double invoke(MethodHandle sample, long seed, double x, double z, int octaves, double bounding) {
        try {
            return (double) sample.invokeExact(seed, x, z, octaves, bounding);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Native billow validation failed", failure);
        }
    }
}
