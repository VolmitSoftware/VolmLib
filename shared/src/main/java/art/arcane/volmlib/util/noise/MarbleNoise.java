package art.arcane.volmlib.util.noise;

import java.util.SplittableRandom;

public final class MarbleNoise extends PatternNoise {
    private static final double TAU = Math.PI * 2D;

    private final FastNoiseDouble distortion;
    private final double directionX;
    private final double directionY;
    private final double directionZ;
    private final double phase;

    public MarbleNoise(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        distortion = new FastNoiseDouble(random.nextLong());
        distortion.setFrequency(1D);
        double angle = random.nextDouble(TAU);
        directionX = Math.cos(angle);
        directionY = random.nextDouble(0.25D, 0.65D);
        directionZ = Math.sin(angle);
        phase = random.nextDouble(TAU);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double broad = distortion.GetSimplex(x * 0.45D, y * 0.45D, z * 0.45D);
        double detail = distortion.GetSimplex(x * 1.3D + 17.3D, y * 1.3D - 11.7D, z * 1.3D + 5.9D);
        double warp = broad * 1.1D + detail * 0.2D;
        double position = x * directionX + y * directionY + z * directionZ;
        double ridge = Math.sin(TAU * (position + warp) + phase);
        double vein = 1D / (1D + 90D * ridge * ridge);
        double stone = 0.85D + 0.15D * broad;
        return Math.max(0D, Math.min(1D, 0.1D + 0.84D * stone * (1D - vein)));
    }
}
