package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.FastNoiseDouble.FractalType;

public final class NativeBillowProbe {
    public static void main(String[] arguments) {
        String expectedBackend = arguments[0];
        boolean diagnostics = Boolean.parseBoolean(arguments[1]);
        if (!FractalBillowSimplexNoise.nativeBackendStatus().equals(expectedBackend)) {
            throw new AssertionError("Expected " + expectedBackend + " got " + FractalBillowSimplexNoise.nativeBackendStatus());
        }
        System.setProperty("volmlib.noise.nativeBillow", Boolean.toString(!Boolean.getBoolean("volmlib.noise.nativeBillow")));
        System.setProperty("volmlib.noise.nativeBillowLibrary", "changed-after-initialization");
        System.setProperty("volmlib.noise.nativeBillowDiagnostics", Boolean.toString(!Boolean.getBoolean("volmlib.noise.nativeBillowDiagnostics")));
        long checks = 0;
        double[] coordinates = {0D, -0D, 1D, -1D, 100D, -100D, Math.nextDown(100D), Math.nextUp(-100D),
                30_000_000D, -30_000_000D, 30_000_001D, Double.MIN_VALUE, -Double.MIN_VALUE,
                Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (long seed : new long[]{0L, -1L, 1337L, Long.MIN_VALUE, Long.MAX_VALUE, 0x9e3779b97f4a7c15L}) {
            FractalBillowSimplexNoise candidate = new FractalBillowSimplexNoise(seed);
            FastNoiseDouble reference = new FastNoiseDouble(new RNG(seed).lmax());
            reference.setFractalType(FractalType.Billow);
            for (int octaves : new int[]{1, 8, 9, 3, 8, 0, -1, 9}) {
                candidate.setOctaves(octaves);
                reference.setFractalOctaves(octaves);
                for (double x : coordinates) {
                    for (double z : coordinates) {
                        exact((reference.GetSimplexFractal(x, z) / 2D) + 0.5D, candidate.noise(x, z));
                        exact((reference.GetSimplexFractal(x, 0D) / 2D) + 0.5D, candidate.noise(x));
                        exact((reference.GetSimplexFractal(x, 12D, z) / 2D) + 0.5D, candidate.noise(x, 12D, z));
                        checks += 3;
                    }
                }
                RNG random = new RNG(seed);
                for (int index = 0; index < 4096; index++) {
                    double x = random.nextDouble() * 60_000_000D - 30_000_000D;
                    double z = random.nextDouble() * 60_000_000D - 30_000_000D;
                    exact((reference.GetSimplexFractal(x, z) / 2D) + 0.5D, candidate.noise(x, z));
                    checks++;
                }
            }
        }
        long nativeSamples = FractalBillowSimplexNoise.nativeSampleCount();
        if (expectedBackend.equals("native") && diagnostics && nativeSamples == 0L) {
            throw new AssertionError("No native samples");
        }
        if ((!expectedBackend.equals("native") || !diagnostics) && nativeSamples != 0L) {
            throw new AssertionError("Fallback sampled native");
        }
        System.out.println("backend=" + expectedBackend + " rawBitChecks=" + checks + " nativeSamples=" + nativeSamples);
    }

    private static void exact(double expected, double actual) {
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
            throw new AssertionError("Raw-bit mismatch: " + Long.toHexString(Double.doubleToRawLongBits(expected)) + " != " + Long.toHexString(Double.doubleToRawLongBits(actual)));
        }
    }
}
