package art.arcane.volmlib.util.noise;

import java.util.SplittableRandom;

public final class StrataNoise extends PatternNoise {
    private static final double TAU = Math.PI * 2D;

    private final double cosine;
    private final double sine;
    private final double phaseA;
    private final double phaseB;
    private final double phaseC;

    public StrataNoise(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        double angle = random.nextDouble(TAU);
        cosine = Math.cos(angle);
        sine = Math.sin(angle);
        phaseA = random.nextDouble(TAU);
        phaseB = random.nextDouble(TAU);
        phaseC = random.nextDouble(TAU);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double along = x * cosine + z * sine;
        double across = z * cosine - x * sine;
        double fold = 1.1D * Math.sin(along * 1.1D + phaseA)
                + 0.35D * Math.sin(along * 2.7D + across * 0.8D + phaseB + y * 0.4D)
                + 0.2D * Math.sin(across * 1.7D + y * 0.9D + phaseC);
        double layer = TAU * (across + y * 0.75D + fold);
        double thickness = layer + 0.55D * Math.sin(layer * 0.25D + phaseB);
        double broadBand = 0.5D + 0.5D * Math.sin(thickness);
        double lamina = 0.5D + 0.5D * Math.sin(thickness * 3D + phaseC);
        return 0.82D * broadBand + 0.18D * lamina;
    }
}
