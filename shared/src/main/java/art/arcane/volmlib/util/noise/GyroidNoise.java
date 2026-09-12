package art.arcane.volmlib.util.noise;

import java.util.SplittableRandom;

public final class GyroidNoise extends PatternNoise {
    private static final double TAU = Math.PI * 2D;

    private final double horizontalCosine;
    private final double horizontalSine;
    private final double tiltCosine;
    private final double tiltSine;
    private final double phaseX;
    private final double phaseY;
    private final double phaseZ;

    public GyroidNoise(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        double horizontalAngle = random.nextDouble(TAU);
        double tiltAngle = random.nextDouble(0.35D, 1.2D);
        horizontalCosine = Math.cos(horizontalAngle);
        horizontalSine = Math.sin(horizontalAngle);
        tiltCosine = Math.cos(tiltAngle);
        tiltSine = Math.sin(tiltAngle);
        phaseX = random.nextDouble(TAU);
        phaseY = random.nextDouble(TAU);
        phaseZ = random.nextDouble(TAU);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double horizontal = x * horizontalCosine + z * horizontalSine;
        double transverse = z * horizontalCosine - x * horizontalSine;
        double u = TAU * (horizontal * tiltCosine + y * tiltSine) + phaseX;
        double v = TAU * (y * tiltCosine - horizontal * tiltSine) + phaseY;
        double w = TAU * transverse + phaseZ;
        double a = u + 0.7D * Math.sin(0.37D * v + 0.29D * w + phaseZ);
        double b = v + 0.7D * Math.sin(0.31D * w - 0.23D * u + phaseX);
        double c = w + 0.7D * Math.sin(0.41D * u + 0.19D * v + phaseY);
        double field = Math.sin(a) * Math.cos(b) + Math.sin(b) * Math.cos(c) + Math.sin(c) * Math.cos(a);
        double width = 0.62D + 0.22D * Math.sin(0.29D * u + 0.23D * v - 0.19D * w);
        double sheet = Math.max(0D, 1D - Math.abs(field) / width);
        return sheet * sheet * (3D - 2D * sheet);
    }
}
