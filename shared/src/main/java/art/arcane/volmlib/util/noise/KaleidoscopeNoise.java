package art.arcane.volmlib.util.noise;

public final class KaleidoscopeNoise extends PatternNoise {
    private static final double TAU = Math.PI * 2D;

    private final long seed;

    public KaleidoscopeNoise(long seed) {
        this.seed = seed;
    }

    @Override
    protected double sample(double x, double y, double z) {
        z += 0.5D;
        long cellX = (long) Math.floor(x);
        long cellZ = (long) Math.floor(z);
        double dx = x - cellX - 0.5D;
        double dz = z - cellZ - 0.5D;
        double radiusSquared = dx * dx + dz * dz;
        if (radiusSquared >= 0.25D) {
            return 0D;
        }

        long hash = mix(seed ^ cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xD1B54A32D192ED03L);
        int wedges = 4 + (int) (hash & 3L);
        double phase = (hash >>> 11) * 0x1.0p-53D * TAU;
        double sector = TAU / wedges;
        double angle = Math.atan2(dz, dx) + phase + y * 0.35D;
        angle = Math.abs(angle - Math.floor(angle / sector + 0.5D) * sector);
        double radius = Math.sqrt(radiusSquared);
        double u = radius * Math.cos(angle);
        double v = radius * Math.sin(angle);
        double motif = 0.5D + 0.5D * Math.cos(16D * u + 2.5D * Math.cos(18D * v + phase + y));
        double envelope = 1D - radiusSquared * 4D;
        return motif * envelope * envelope;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
