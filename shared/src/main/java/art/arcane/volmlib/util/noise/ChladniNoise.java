package art.arcane.volmlib.util.noise;

public final class ChladniNoise extends PatternNoise {
    private final int firstMode;
    private final int secondMode;
    private final double phase;
    private final double width;

    public ChladniNoise(long seed) {
        long hash = mix(seed);
        firstMode = 2 + (int) (hash & 1L);
        secondMode = firstMode + 1 + (int) ((hash >>> 1) & 1L);
        phase = (hash >>> 11) * 0x1.0p-52D;
        width = 0.22D + ((hash >>> 3) & 255L) * (0.12D / 255D);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double u = (x + phase) * Math.PI;
        double v = (z + phase) * Math.PI;
        double first = Math.cos(firstMode * u) * Math.cos(secondMode * v);
        double second = Math.cos(secondMode * u) * Math.cos(firstMode * v);
        double field = first * Math.cos(y * Math.PI * 0.5D)
                - second * Math.cos(y * Math.PI * 0.75D);
        double node = Math.max(0D, 1D - Math.abs(field) / width);
        return node * node * (3D - 2D * node);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
