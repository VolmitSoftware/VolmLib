/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.VolmLog;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.FastNoiseDouble.FractalType;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicLong;

public class FractalBillowSimplexNoise implements NoiseGenerator, OctaveNoise {
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("volmlib.noise.nativeBillowDiagnostics");
    private static final AtomicLong NATIVE_SAMPLES = new AtomicLong();
    private static final NativeBackend NATIVE_BACKEND = createNativeBackend();
    private static final MethodHandle NATIVE_SAMPLE = NATIVE_BACKEND.sample();
    private final FastNoiseDouble n;
    private final long nativeSeed;
    private int octaves = 1;
    private double nativeBounding = 1D;

    public FractalBillowSimplexNoise(long seed) {
        nativeSeed = new RNG(seed).lmax();
        this.n = new FastNoiseDouble(nativeSeed);
        n.setFractalOctaves(1);
        n.setFractalType(FractalType.Billow);
    }

    public double f(double v) {
        return (v / 2D) + 0.5D;
    }

    @Override
    public double noise(double x) {
        return f(n.GetSimplexFractal(x, 0d));
    }

    @Override
    public double noise(double x, double z) {
        if (NATIVE_SAMPLE != null && (octaves == 8 || octaves == 9)
                && Math.abs(x) <= 30_000_000D && Math.abs(z) <= 30_000_000D) {
            try {
                double result = (double) NATIVE_SAMPLE.invokeExact(nativeSeed, x, z, octaves, nativeBounding);
                if (DIAGNOSTICS) {
                    NATIVE_SAMPLES.incrementAndGet();
                }
                return result;
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Native billow sampling failed", failure);
            }
        }
        return f(n.GetSimplexFractal(x, z));
    }

    @Override
    public double noise(double x, double y, double z) {
        return f(n.GetSimplexFractal(x, y, z));
    }

    @Override
    public void setOctaves(int o) {
        n.setFractalOctaves(o);
        octaves = o;
        double amplitude = 0.5D;
        double amplitudeSum = 1D;
        for (int octave = 1; octave < o; octave++) {
            amplitudeSum += amplitude;
            amplitude *= 0.5D;
        }
        nativeBounding = 1D / amplitudeSum;
    }

    public static String nativeBackendStatus() {
        return NATIVE_BACKEND.status();
    }

    public static long nativeSampleCount() {
        return NATIVE_SAMPLES.get();
    }

    private static NativeBackend createNativeBackend() {
        if (!Boolean.parseBoolean(System.getProperty("volmlib.noise.nativeBillow", "true"))) {
            return new NativeBackend(null, "java-disabled");
        }
        if (Runtime.version().feature() < 25) {
            return new NativeBackend(null, "java-unsupported-runtime");
        }
        try {
            return (NativeBackend) Class.forName("art.arcane.volmlib.util.noise.NativeBillowLoader")
                    .getMethod("load").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            VolmLog.warning("Noise", "Native billow initialization failed; using Java", failure);
            return new NativeBackend(null, "java-initialization-failed");
        }
    }

    record NativeBackend(MethodHandle sample, String status) {
    }
}
