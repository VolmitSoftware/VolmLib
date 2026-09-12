package art.arcane.volmlib.util.noise;

public final class ScalesNoise extends PatternNoise {
    private static final double TAU = Math.PI * 2D;
    private static final double RADIUS = 0.5D;
    private static final double ROW_HEIGHT = 0.5D;
    private static final double GROOVE_WIDTH = 0.055D;

    private final long seed;
    private final double phaseX;
    private final double phaseZ;

    public ScalesNoise(long seed) {
        this.seed = seed;
        phaseX = (mix(seed) >>> 11) * 0x1.0p-53D;
        phaseZ = (mix(seed + 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53D;
    }

    @Override
    protected double sample(double x, double y, double z) {
        x += phaseX + Math.sin(y * TAU * 0.5D) * 0.22D + y * 0.15D;
        z += phaseZ + Math.sin(y * TAU * 0.25D) * 0.12D + y * 0.2D;
        long row = (long) Math.floor(z / ROW_HEIGHT);
        double groove = 0D;
        double shade = 0D;
        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            long pointZ = row + offsetZ;
            double shift = (pointZ & 1L) * 0.5D;
            long column = (long) Math.floor(x - shift);
            double dz = z - pointZ * ROW_HEIGHT;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                long pointX = column + offsetX;
                double dx = x - (pointX + shift);
                double radiusSquared = dx * dx + dz * dz;
                double distance;
                if (dz >= 0D) {
                    distance = Math.abs(Math.sqrt(radiusSquared) - RADIUS);
                } else {
                    double endpoint = Math.abs(dx) - RADIUS;
                    distance = Math.sqrt(endpoint * endpoint + dz * dz);
                }
                double rim = Math.max(0D, 1D - distance / GROOVE_WIDTH);
                if (rim > 0D || radiusSquared < RADIUS * RADIUS && dz > 0D) {
                    long hash = mix(seed ^ pointX * 0x9E3779B97F4A7C15L ^ pointZ * 0xC2B2AE3D27D4EB4FL);
                    double variation = 0.75D + (hash >>> 11) * 0x1.0p-53D * 0.25D;
                    groove = Math.max(groove, rim * rim * (3D - 2D * rim) * variation);
                    double inside = Math.max(0D, 1D - radiusSquared / (RADIUS * RADIUS));
                    double rise = Math.max(0D, Math.min(1D, dz / 0.12D));
                    shade = Math.max(shade, inside * rise * rise * (3D - 2D * rise) * variation);
                }
            }
        }
        return 0.72D + 0.22D * shade - 0.65D * groove;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
