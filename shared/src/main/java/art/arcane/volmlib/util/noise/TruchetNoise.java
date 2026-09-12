package art.arcane.volmlib.util.noise;

public final class TruchetNoise extends PatternNoise {
    private static final double RIBBON_WIDTH = 0.075D;
    private static final double HEIGHT_FREQUENCY = Math.PI * 0.25D;

    private final long seed;
    private final double phaseX;
    private final double phaseZ;

    public TruchetNoise(long seed) {
        this.seed = seed;
        phaseX = 0.17D + (mix(seed) >>> 11) * 0x1.0p-53D * 0.61D;
        phaseZ = 0.13D + (mix(seed + 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53D * 0.73D;
    }

    @Override
    protected double sample(double x, double y, double z) {
        x += phaseX;
        z += phaseZ;
        if (y != 0D) {
            double angle = y * HEIGHT_FREQUENCY;
            x += Math.sin(angle) * 0.3D + y * 0.125D;
            z += (Math.cos(angle) - 1D) * 0.3D;
        }

        long cellX = (long) Math.floor(x);
        long cellZ = (long) Math.floor(z);
        double localX = x - cellX;
        double localZ = z - cellZ;
        long orientation = mix(seed ^ cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xC2B2AE3D27D4EB4FL);
        if ((orientation & 1L) != 0L) {
            localX = 1D - localX;
        }

        double oppositeX = 1D - localX;
        double oppositeZ = 1D - localZ;
        double first = Math.abs(Math.sqrt(localX * localX + localZ * localZ) - 0.5D);
        double second = Math.abs(Math.sqrt(oppositeX * oppositeX + oppositeZ * oppositeZ) - 0.5D);
        double ribbon = Math.max(0D, 1D - Math.min(first, second) / RIBBON_WIDTH);
        return ribbon * ribbon * (3D - 2D * ribbon);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
