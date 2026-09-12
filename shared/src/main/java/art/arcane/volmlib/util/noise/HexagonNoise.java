package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;

public class HexagonNoise extends HexagonalNoise implements OctaveNoise {
    private static final double HEAT_SCALE = 18D;
    private final SimplexNoise heat;

    public HexagonNoise(long seed) {
        this.heat = new SimplexNoise(new RNG(seed).lmax());
    }

    @Override
    public void setOctaves(int octaves) {
        heat.setOctaves(octaves);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double q = (SQRT_3_OVER_3 * x) - (z / 3D);
        double r = TWO_OVER_THREE * z;
        long centerQ = roundQ(q, r);
        long centerR = roundR(q, r);
        double centerX = SQRT_3 * (centerQ + (centerR * 0.5D));
        double centerZ = 1.5D * centerR;
        return heat.noise(centerX * HEAT_SCALE, y * HEAT_SCALE, centerZ * HEAT_SCALE);
    }
}
