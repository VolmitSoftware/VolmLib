package art.arcane.volmlib.util.noise;

public final class MengerSpongeNoise extends PatternNoise {
    private static final int DEPTH = 3;

    private final double phaseX;
    private final double phaseY;
    private final double phaseZ;

    public MengerSpongeNoise(long seed) {
        long hash = mix(seed);
        phaseX = (hash & 0x1FFFFFL) * 0x1.0p-21D;
        phaseY = ((hash >>> 21) & 0x1FFFFFL) * (0x1.0p-21D / 3D);
        phaseZ = (hash >>> 42) * 0x1.0p-22D;
    }

    @Override
    protected double sample(double x, double y, double z) {
        x += phaseX;
        y += phaseY;
        z += phaseZ;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        for (int level = 0; level < DEPTH; level++) {
            x *= 3D;
            y *= 3D;
            z *= 3D;
            int cellX = (int) x;
            int cellY = (int) y;
            int cellZ = (int) z;
            int middleAxes = (cellX == 1 ? 1 : 0) + (cellY == 1 ? 1 : 0) + (cellZ == 1 ? 1 : 0);
            if (middleAxes >= 2) {
                return 0D;
            }
            x -= cellX;
            y -= cellY;
            z -= cellZ;
        }
        return 1D;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
