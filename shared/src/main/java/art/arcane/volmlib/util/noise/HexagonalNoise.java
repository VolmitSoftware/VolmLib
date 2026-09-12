package art.arcane.volmlib.util.noise;

abstract class HexagonalNoise implements NoiseGenerator {
    protected static final double SQRT_3 = Math.sqrt(3D);
    protected static final double SQRT_3_OVER_3 = SQRT_3 / 3D;
    protected static final double TWO_OVER_THREE = 2D / 3D;
    protected static final double ONE_THIRD = 1D / 3D;
    protected static final double[] CHILD_Q = {0D, 4D / 9D, 2D / 9D, -2D / 9D, -4D / 9D, -2D / 9D, 2D / 9D};
    protected static final double[] CHILD_R = {0D, -2D / 9D, 2D / 9D, 4D / 9D, 2D / 9D, -2D / 9D, -4D / 9D};
    protected static final long CONST_X = 0x9E3779B97F4A7C15L;
    protected static final long CONST_Z = 0xC2B2AE3D27D4EB4FL;

    @Override
    public double noise(double x) {
        return sample(x, 0D, 0D);
    }

    @Override
    public double noise(double x, double z) {
        return sample(x, 0D, z);
    }

    @Override
    public double noise(double x, double y, double z) {
        return sample(x, y, z);
    }

    protected abstract double sample(double x, double y, double z);

    protected static long roundQ(double q, double r) {
        double cubeY = -q - r;
        long roundedQ = Math.round(q);
        long roundedR = Math.round(r);
        long roundedY = Math.round(cubeY);
        double qDiff = Math.abs(roundedQ - q);
        double rDiff = Math.abs(roundedR - r);
        double yDiff = Math.abs(roundedY - cubeY);
        return qDiff > yDiff && qDiff > rDiff ? -roundedY - roundedR : roundedQ;
    }

    protected static long roundR(double q, double r) {
        double cubeY = -q - r;
        long roundedQ = Math.round(q);
        long roundedR = Math.round(r);
        long roundedY = Math.round(cubeY);
        double qDiff = Math.abs(roundedQ - q);
        double rDiff = Math.abs(roundedR - r);
        double yDiff = Math.abs(roundedY - cubeY);
        return !(qDiff > yDiff && qDiff > rDiff) && yDiff <= rDiff ? -roundedQ - roundedY : roundedR;
    }

    protected static long mix(long input) {
        input = (input ^ (input >>> 33)) * 0xff51afd7ed558ccdL;
        input = (input ^ (input >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return input ^ (input >>> 33);
    }

    protected static int pickChildIndex(double localQ, double localR) {
        for (int index = 0; index < CHILD_Q.length; index++) {
            double deltaQ = localQ - CHILD_Q[index];
            double deltaR = localR - CHILD_R[index];
            double distance = Math.max(Math.abs((2D * deltaQ) + deltaR),
                    Math.max(Math.abs(deltaQ + (2D * deltaR)), Math.abs(deltaQ - deltaR)));
            if (distance <= ONE_THIRD) {
                return index;
            }
        }

        return -1;
    }
}
