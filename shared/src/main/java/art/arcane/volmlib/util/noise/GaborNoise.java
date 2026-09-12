package art.arcane.volmlib.util.noise;

import java.util.SplittableRandom;

public final class GaborNoise extends PatternNoise {
    private static final double TAU = Math.PI * 2D;
    private static final double RADIUS_SQUARED = 0.95D * 0.95D;
    private static final double SPREAD = 0.6D / 65535D;

    private final long seed;
    private final double directionX;
    private final double directionY;
    private final double directionZ;

    public GaborNoise(long seed) {
        this.seed = seed;
        SplittableRandom random = new SplittableRandom(seed);
        double angle = random.nextDouble(TAU);
        double elevation = random.nextDouble(-0.55D, 0.55D);
        double horizontal = Math.sqrt(1D - elevation * elevation);
        directionX = Math.cos(angle) * horizontal;
        directionY = elevation;
        directionZ = Math.sin(angle) * horizontal;
    }

    @Override
    protected double sample(double x, double y, double z) {
        long cellX = (long) Math.floor(x);
        long cellY = (long) Math.floor(y);
        long cellZ = (long) Math.floor(z);
        double sum = 0D;
        double weights = 0.12D;
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            long pointY = cellY + offsetY;
            long hashY = pointY * 0xC2B2AE3D27D4EB4FL;
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                long pointZ = cellZ + offsetZ;
                long hashYZ = hashY ^ pointZ * 0x165667B19E3779F9L;
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    long pointX = cellX + offsetX;
                    long hash = mix(seed ^ pointX * 0x9E3779B97F4A7C15L ^ hashYZ);
                    if ((hash & 3L) == 0L) {
                        continue;
                    }
                    double dx = x - (pointX + 0.2D + (hash & 65535L) * SPREAD);
                    double dy = y - (pointY + 0.2D + ((hash >>> 16) & 65535L) * SPREAD);
                    double dz = z - (pointZ + 0.2D + ((hash >>> 32) & 65535L) * SPREAD);
                    double distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared >= RADIUS_SQUARED) {
                        continue;
                    }
                    double envelope = 1D - distanceSquared / RADIUS_SQUARED;
                    double weight = envelope * envelope * envelope;
                    long wave = mix(hash);
                    double waveX = directionX + ((wave & 1023L) / 1023D - 0.5D) * 0.35D;
                    double waveY = directionY + (((wave >>> 10) & 1023L) / 1023D - 0.5D) * 0.35D;
                    double waveZ = directionZ + (((wave >>> 20) & 1023L) / 1023D - 0.5D) * 0.35D;
                    double phase = (wave >>> 32) * (TAU / 4294967296D);
                    sum += weight * Math.cos(TAU * 3D * (waveX * dx + waveY * dy + waveZ * dz) + phase);
                    weights += weight;
                }
            }
        }
        return 0.5D + 0.5D * sum / weights;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
