package art.arcane.volmlib.util.noise;

public final class VortexNoise extends PatternNoise {
    private static final double RADIUS = 0.9D;
    private static final double RADIUS_SQUARED = RADIUS * RADIUS;
    private static final double TAU = Math.PI * 2D;

    private final long seed;

    public VortexNoise(long seed) {
        this.seed = seed;
    }

    @Override
    protected double sample(double x, double y, double z) {
        long cellX = (long) Math.floor(x);
        long cellZ = (long) Math.floor(z);
        double sum = 0D;
        double weightSum = 0.07D;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            long gridX = cellX + offsetX;
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                long gridZ = cellZ + offsetZ;
                long hash = hash(gridX, gridZ);
                double dx = x - (gridX + 0.2D + unit(hash) * 0.6D);
                hash = mix(hash);
                double dz = z - (gridZ + 0.2D + unit(hash) * 0.6D);
                double radiusSquared = dx * dx + dz * dz;
                if (radiusSquared >= RADIUS_SQUARED) {
                    continue;
                }
                double radius = Math.sqrt(radiusSquared);
                double envelope = 1D - radiusSquared / RADIUS_SQUARED;
                double weight = envelope * envelope;
                hash = mix(hash);
                int arms = 2 + (int) (hash & 1L);
                int direction = (hash & 2L) == 0L ? -1 : 1;
                double phase = TAU * unit(hash);
                hash = mix(hash);
                double winding = 12D + unit(hash) * 4D;
                double wave = Math.sin(direction * arms * Math.atan2(dz, dx)
                        - winding * radius + phase + y * TAU * 0.5D);
                sum += weight * radius / (radius + 0.06D) * wave;
                weightSum += weight;
            }
        }
        return 0.5D + 0.5D * sum / weightSum;
    }

    private long hash(long x, long z) {
        return mix(seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xD1B54A32D192ED03L);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
