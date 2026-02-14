package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.NoiseInjector;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;

public class CNG implements NoiseGenerator {
    public static final NoiseInjector ADD = (src, value) -> new double[]{src + value, value};
    public static final NoiseInjector MULTIPLY = (src, value) -> new double[]{src * value, value};
    public static final NoiseInjector MAX = (src, value) -> new double[]{Math.max(src, value), value};
    public static final NoiseInjector MIN = (src, value) -> new double[]{Math.min(src, value), value};
    public static final NoiseInjector SRC_MOD = (src, value) -> new double[]{value == 0D ? src : src % value, value};
    public static final NoiseInjector DST_MOD = (src, value) -> new double[]{src, src == 0D ? value : value % src};
    public static final NoiseInjector SRC_SUBTRACT = (src, value) -> new double[]{src - value, value};
    public static final NoiseInjector DST_SUBTRACT = (src, value) -> new double[]{src, value - src};
    public static final NoiseInjector SRC_POW = (src, value) -> new double[]{Math.pow(src, value), value};
    public static final NoiseInjector DST_POW = (src, value) -> new double[]{src, Math.pow(value, src)};

    private final KList<ChildNoise> children = new KList<>();
    private NoiseGenerator base;
    private NoiseInjector injector = ADD;
    private double scale = 1D;
    private double patch = 0D;

    public CNG(NoiseGenerator base) {
        this.base = base;
    }

    public static CNG signature(RNG rng) {
        return new CNG(new PerlinNoise(rng.lmax()));
    }

    public CNG child(CNG noise) {
        children.add(new ChildNoise(noise, 1D));
        return this;
    }

    public CNG fractureWith(CNG noise, double amount) {
        children.add(new ChildNoise(noise, amount == 0D ? 1D : amount));
        return this;
    }

    public CNG setInjector(NoiseInjector injector) {
        this.injector = injector == null ? ADD : injector;
        return this;
    }

    public CNG oct(int octaves) {
        if (base instanceof OctaveNoise o) {
            o.setOctaves(Math.max(1, octaves));
        }
        return this;
    }

    public CNG scale(double scale) {
        if (scale != 0D) {
            this.scale *= scale;
        }
        return this;
    }

    public CNG patch(double patch) {
        this.patch = patch;
        return this;
    }

    @Override
    public double noise(double x) {
        return noise(x, 0D);
    }

    @Override
    public double noise(double x, double z) {
        double sx = x * scale;
        double sz = z * scale;
        double src = normalize(base.noise(sx, sz));
        for (ChildNoise i : children) {
            double child = normalize(i.noise.noise(sx, sz));
            double[] combined = injector.combine(src, child / i.amount);
            src = normalize(combined[0]);
        }
        return normalize(src + patch);
    }

    @Override
    public double noise(double x, double y, double z) {
        double sx = x * scale;
        double sy = y * scale;
        double sz = z * scale;
        double src = normalize(base.noise(sx, sy, sz));
        for (ChildNoise i : children) {
            double child = normalize(i.noise.noise(sx, sy, sz));
            double[] combined = injector.combine(src, child / i.amount);
            src = normalize(combined[0]);
        }
        return normalize(src + patch);
    }

    public int fit(double min, double max, double x, double z) {
        return (int) Math.round(fitDouble(min, max, x, z));
    }

    public int fit(double min, double max, double x, double z, double t) {
        return (int) Math.round(fitDouble(min, max, x, z, t));
    }

    public double fitDouble(double min, double max, double x, double z) {
        return fitDouble(min, max, x, z, 0D);
    }

    public double fitDouble(double min, double max, double x, double z, double t) {
        return M.lerp(min, max, noise(x + t, z + t));
    }

    private static double normalize(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, v));
    }

    private record ChildNoise(CNG noise, double amount) {
    }
}
