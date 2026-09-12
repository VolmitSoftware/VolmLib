package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;

public class SierpinskiTriangleNoise implements NoiseGenerator, OctaveNoise {
    private static final double TRIANGLE_SIDE = 8D;
    private static final double INVERSE_HEIGHT = 2D / (Math.sqrt(3D) * TRIANGLE_SIDE);
    private static final double HEAT_SCALE = 6D;
    private static final int DEPTH = 4;
    private final SimplexNoise heat;

    public SierpinskiTriangleNoise(long seed) {
        this.heat = new SimplexNoise(new RNG(seed).nextParallelRNG(177L).lmax());
    }

    @Override
    public double noise(double x) {
        return sample(x, 0D, 0D);
    }

    @Override
    public double noise(double x, double z) {
        return sample(x, 0D, z);
    }

    @Override
    public double noise(double x, double y, double z) {
        return sample(x, y, z);
    }

    @Override
    public void setOctaves(int octaves) {
        heat.setOctaves(octaves);
    }

    private boolean contains(double x, double z) {
        double v = z * INVERSE_HEIGHT;
        double u = (x / TRIANGLE_SIDE) - (v * 0.5D);
        u -= Math.floor(u);
        v -= Math.floor(v);

        if (u + v > 1D) {
            u = 1D - u;
            v = 1D - v;
        }

        for (int depth = 0; depth < DEPTH; depth++) {
            if (u >= 0.5D) {
                u = (u * 2D) - 1D;
                v *= 2D;
            } else if (v >= 0.5D) {
                u *= 2D;
                v = (v * 2D) - 1D;
            } else if (u + v <= 0.5D) {
                u *= 2D;
                v *= 2D;
            } else {
                return false;
            }
        }

        return true;
    }

    private double sample(double x, double y, double z) {
        double value = heat.noise(x * HEAT_SCALE, y * HEAT_SCALE, z * HEAT_SCALE);
        return contains(x, z) ? (value * 0.65D) + 0.35D : value * 0.12D;
    }
}
