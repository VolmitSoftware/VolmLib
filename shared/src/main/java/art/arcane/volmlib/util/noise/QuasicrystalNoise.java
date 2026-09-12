package art.arcane.volmlib.util.noise;

import java.util.SplittableRandom;

public final class QuasicrystalNoise extends PatternNoise {
    private static final int WAVES = 5;
    private static final double TAU = Math.PI * 2D;

    private final double[] waveX = new double[WAVES];
    private final double[] waveZ = new double[WAVES];
    private final double phase;
    private final double verticalFrequency;
    private final double contourPhase;

    public QuasicrystalNoise(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        double rotation = random.nextDouble(TAU);
        for (int i = 0; i < WAVES; i++) {
            double angle = rotation + i * TAU / WAVES;
            waveX[i] = Math.cos(angle) * TAU;
            waveZ[i] = Math.sin(angle) * TAU;
        }
        phase = random.nextDouble(TAU);
        verticalFrequency = random.nextDouble(0.3D, 0.7D) * TAU;
        contourPhase = random.nextDouble(TAU);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double layerPhase = phase + y * verticalFrequency;
        double sum = 0D;
        for (int i = 0; i < WAVES; i++) {
            sum += Math.cos(x * waveX[i] + z * waveZ[i] + layerPhase);
        }
        return 0.5D + 0.5D * Math.sin(sum * 1.4D + contourPhase);
    }
}
