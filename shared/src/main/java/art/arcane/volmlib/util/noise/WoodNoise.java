package art.arcane.volmlib.util.noise;

public final class WoodNoise extends PatternNoise {
    private static final double TAU = Math.PI * 2D;

    private final long seed;
    private final double cosine;
    private final double sine;
    private final double phase;

    public WoodNoise(long seed) {
        this.seed = seed;
        phase = (mix(seed) >>> 11) * 0x1.0p-53D * TAU;
        cosine = Math.cos(phase);
        sine = Math.sin(phase);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double along = (z * cosine - x * sine) * 0.45D + y * 0.17D;
        double across = x * cosine + z * sine + y * 0.07D + 0.11D * Math.sin(along * 0.85D + phase);
        double grainPhase = across * TAU * 3D + 0.18D * Math.sin(along * 2.1D + phase);
        double value = 0.12D * (0.5D + 0.5D * Math.sin(grainPhase));
        double weightSum = 0.12D;
        long cellAcross = (long) Math.floor(across);
        long cellAlong = (long) Math.floor(along);
        for (int offsetAcross = -1; offsetAcross <= 1; offsetAcross++) {
            long gridAcross = cellAcross + offsetAcross;
            for (int offsetAlong = -1; offsetAlong <= 1; offsetAlong++) {
                long gridAlong = cellAlong + offsetAlong;
                long hash = mix(seed ^ gridAcross * 0x9E3779B97F4A7C15L ^ gridAlong * 0xD1B54A32D192ED03L);
                double deltaAcross = across - (gridAcross + 0.3D + (hash & 65535L) * (0.4D / 65535D));
                double deltaAlong = along - (gridAlong + 0.3D + ((hash >>> 16) & 65535L) * (0.4D / 65535D));
                double radius = 0.5D + ((hash >>> 32) & 255L) * (0.12D / 255D);
                double distanceSquared = deltaAcross * deltaAcross + deltaAlong * deltaAlong;
                double envelope = 1D - distanceSquared / (radius * radius);
                if (envelope <= 0D) {
                    continue;
                }
                double rings = Math.sqrt(distanceSquared + 0.0004D) * TAU * 5D;
                double ringPhase = ((hash >>> 40) & 65535L) * (TAU / 65535D);
                double knot = 0.5D + 0.5D * Math.sin(rings + ringPhase);
                double weight = 3D * envelope * envelope;
                value += knot * weight;
                weightSum += weight;
            }
        }
        return value / weightSum;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
