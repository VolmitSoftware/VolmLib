package art.arcane.volmlib.util.noise;

public final class CraterNoise extends PatternNoise {
    private static final double CENTER_SPREAD = 0.6D / 65535D;
    private static final double MAX_SUPPORT_SQUARED = 0.6D * 0.6D * 1.35D;

    private final long seed;

    public CraterNoise(long seed) {
        this.seed = seed;
    }

    @Override
    protected double sample(double x, double y, double z) {
        long cellX = (long) Math.floor(x);
        long cellY = (long) Math.floor(y);
        long cellZ = (long) Math.floor(z);
        double bowl = 0D;
        double rim = 0D;

        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            long pointY = cellY + offsetY;
            long hashY = pointY * 0xC2B2AE3D27D4EB4FL;
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                long pointZ = cellZ + offsetZ;
                long hashYZ = hashY ^ pointZ * 0x165667B19E3779F9L;
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    long pointX = cellX + offsetX;
                    long hash = mix(seed ^ pointX * 0x9E3779B97F4A7C15L ^ hashYZ);
                    double deltaX = x - (pointX + 0.2D + (hash & 65535L) * CENTER_SPREAD);
                    double deltaY = y - (pointY + 0.2D + ((hash >>> 16) & 65535L) * CENTER_SPREAD);
                    double deltaZ = z - (pointZ + 0.2D + ((hash >>> 32) & 65535L) * CENTER_SPREAD);
                    double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                    if (distanceSquared >= MAX_SUPPORT_SQUARED) {
                        continue;
                    }

                    double radius = 0.36D + (hash >>> 56) * (0.24D / 255D);
                    double relativeDistance = distanceSquared / (radius * radius);
                    if (relativeDistance >= 1.35D) {
                        continue;
                    }

                    double depth = ((hash >>> 48) & 255L) / 255D;
                    double depression = Math.max(0D, 1D - relativeDistance);
                    bowl = Math.min(bowl, -(0.28D + depth * 0.17D) * depression * depression);
                    double rimDistance = (relativeDistance - 1D) / 0.35D;
                    double ridge = Math.max(0D, 1D - rimDistance * rimDistance);
                    rim = Math.max(rim, (0.18D + (1D - depth) * 0.12D) * ridge * ridge);
                }
            }
        }

        return 0.5D + bowl + rim;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
