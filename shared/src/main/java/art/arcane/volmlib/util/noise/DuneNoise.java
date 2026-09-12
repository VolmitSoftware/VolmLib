package art.arcane.volmlib.util.noise;

public final class DuneNoise extends PatternNoise {
    private static final double TAU = Math.PI * 2D;

    private final long seed;
    private final double cosine;
    private final double sine;
    private final double phase;

    public DuneNoise(long seed) {
        this.seed = seed;
        phase = unit(mix(seed)) * TAU;
        cosine = Math.cos(phase);
        sine = Math.sin(phase);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double wind = x * cosine + z * sine + y * 0.17D;
        double across = z * cosine - x * sine + 0.12D * Math.sin(y * 0.7D + phase);
        long cellWind = (long) Math.floor(wind);
        long cellAcross = (long) Math.floor(across);
        double dune = 0D;
        for (int offsetWind = -1; offsetWind <= 1; offsetWind++) {
            long gridWind = cellWind + offsetWind;
            for (int offsetAcross = -1; offsetAcross <= 1; offsetAcross++) {
                long gridAcross = cellAcross + offsetAcross;
                long hash = mix(seed ^ gridWind * 0x9E3779B97F4A7C15L ^ gridAcross * 0xD1B54A32D192ED03L);
                double centerWind = gridWind + 0.2D + (hash & 65535L) * (0.6D / 65535D);
                double centerAcross = gridAcross + 0.2D + ((hash >>> 16) & 65535L) * (0.6D / 65535D);
                double width = 0.32D + ((hash >>> 32) & 255L) * (0.16D / 255D);
                double transverse = (across - centerAcross) / width;
                double transverseSquared = transverse * transverse;
                if (transverseSquared >= 1D) {
                    continue;
                }
                double crest = centerWind + 0.43D * transverseSquared;
                double offset = wind - crest;
                double windward = 0.52D + ((hash >>> 40) & 255L) * (0.14D / 255D);
                double leeward = 0.1D + ((hash >>> 48) & 255L) * (0.035D / 255D);
                double slope = Math.max(0D, 1D - Math.abs(offset) / (offset < 0D ? windward : leeward));
                double horns = 1D - transverseSquared * transverseSquared;
                double height = 0.62D + (hash >>> 56) * (0.38D / 255D);
                dune = Math.max(dune, height * horns * horns * slope * slope * (3D - 2D * slope));
            }
        }
        double ripples = 0.04D + 0.035D * Math.cos(wind * TAU * 2D + 0.5D * Math.sin(across * 1.7D + phase));
        return ripples + (1D - ripples) * dune;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53D;
    }
}
