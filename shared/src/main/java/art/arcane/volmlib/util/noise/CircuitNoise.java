package art.arcane.volmlib.util.noise;

public final class CircuitNoise extends PatternNoise {
    private final long seed;
    private final double phaseX;
    private final double phaseZ;

    public CircuitNoise(long seed) {
        this.seed = seed;
        phaseX = (mix(seed) >>> 11) * 0x1.0p-53;
        phaseZ = (mix(seed + 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    @Override
    protected double sample(double x, double y, double z) {
        x += phaseX;
        z += phaseZ;
        if (y != 0D) {
            x += y * 0.13D + 0.17D * Math.sin(y * Math.PI * 0.5D);
            z += 0.11D * Math.sin(y * Math.PI * 0.75D);
        }
        long cellX = (long) Math.floor(x);
        long cellZ = (long) Math.floor(z);
        x -= cellX;
        z -= cellZ;
        long hash = mix(seed ^ cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xD1B54A32D192ED03L);
        double distance;
        double padDistance;
        if ((hash & 3L) == 0L) {
            distance = Math.min(Math.abs(x - 0.5D), Math.abs(z - 0.5D));
            double dx = x - 0.5D;
            double dz = z - 0.5D;
            padDistance = Math.sqrt(dx * dx + dz * dz);
        } else {
            if ((hash & 1L) != 0L) {
                x = 1D - x;
            }
            distance = Math.sqrt(Math.min(routeDistanceSquared(x, z), routeDistanceSquared(1D - x, 1D - z)));
            double dx = x - 0.75D;
            double dz = z - 0.25D;
            double oppositeX = x - 0.25D;
            double oppositeZ = z - 0.75D;
            padDistance = Math.sqrt(Math.min(dx * dx + dz * dz, oppositeX * oppositeX + oppositeZ * oppositeZ));
        }
        double trace = smooth(1D - distance / 0.045D);
        double pad = smooth(1D - Math.abs(padDistance - 0.085D) / 0.03D);
        return Math.max(trace, pad) * smooth(padDistance / 0.045D);
    }

    private static double routeDistanceSquared(double x, double z) {
        double top = segmentDistanceSquared(x - 0.5D, z, 0D, 0.25D);
        double bend = segmentDistanceSquared(z - 0.25D, x, 0.5D, 0.75D);
        double middle = segmentDistanceSquared(x - 0.75D, z, 0.25D, 0.5D);
        double right = segmentDistanceSquared(z - 0.5D, x, 0.75D, 1D);
        return Math.min(Math.min(top, bend), Math.min(middle, right));
    }

    private static double segmentDistanceSquared(double perpendicular, double along, double start, double end) {
        double excess = Math.max(start - along, Math.max(along - end, 0D));
        return perpendicular * perpendicular + excess * excess;
    }

    private static double smooth(double value) {
        double bounded = Math.max(0D, Math.min(1D, value));
        return bounded * bounded * (3D - 2D * bounded);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
