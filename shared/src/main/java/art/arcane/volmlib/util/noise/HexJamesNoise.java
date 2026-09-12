package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;

public class HexJamesNoise extends HexagonalNoise implements OctaveNoise {
    private static final int MAX_DEPTH = 7;
    private static final double HEAT_SCALE = 18D;
    private static final double LARGE_CONTINUE = 0.26D;
    private static final double SMALL_CONTINUE = 0.79D;
    private static final double CENTER_CONTINUE = 0.58D;
    private final long seed;
    private final SimplexNoise heat;

    public HexJamesNoise(long seed) {
        RNG rng = new RNG(seed);
        this.seed = rng.lmax();
        this.heat = new SimplexNoise(rng.nextParallelRNG(877L).lmax());
    }

    @Override
    public void setOctaves(int octaves) {
        heat.setOctaves(octaves);
    }

    @Override
    protected double sample(double x, double y, double z) {
        double qWorld = (SQRT_3_OVER_3 * x) - (z / 3D);
        double rWorld = TWO_OVER_THREE * z;
        long rootQ = roundQ(qWorld, rWorld);
        long rootR = roundR(qWorld, rWorld);
        double centerQ = rootQ;
        double centerR = rootR;
        double radius = 1D;
        double localQ = qWorld - centerQ;
        double localR = rWorld - centerR;
        long nodeHash = mix(seed ^ (rootQ * CONST_X) ^ (rootR * CONST_Z));
        int level = 0;

        while (level < MAX_DEPTH) {
            int childIndex = pickChildIndex(localQ, localR);
            if (childIndex < 0) {
                break;
            }

            double childQ = CHILD_Q[childIndex];
            double childR = CHILD_R[childIndex];
            centerQ += childQ * radius;
            centerR += childR * radius;
            radius *= ONE_THIRD;
            localQ = (localQ - childQ) * 3D;
            localR = (localR - childR) * 3D;
            nodeHash = mix(nodeHash ^ (CONST_X * (childIndex + 11L)) ^ (CONST_Z * (level + 1L)));
            level++;

            if (!shouldContinue(childIndex, nodeHash, level - 1)) {
                break;
            }
        }

        double centerX = SQRT_3 * (centerQ + (centerR * 0.5D));
        double centerZ = 1.5D * centerR;
        double simplexValue = heat.noise(centerX * HEAT_SCALE, y * HEAT_SCALE, centerZ * HEAT_SCALE);
        return (simplexValue * 0.82D) + (random01(nodeHash, 31 + level) * 0.18D);
    }

    private boolean shouldContinue(int childIndex, long nodeHash, int level) {
        if (level >= MAX_DEPTH - 1) {
            return false;
        }

        double gate = CENTER_CONTINUE;
        if (childIndex != 0) {
            int rotation = (int) (random01(nodeHash, 3) * 6D);
            int parity = random01(nodeHash, 5) >= 0.5D ? 1 : 0;
            int direction = childIndex - 1 - rotation;
            if (direction < 0) {
                direction += 6;
            }
            gate = (direction % 2) == parity ? LARGE_CONTINUE : SMALL_CONTINUE;
        }

        return random01(nodeHash, 97 + level) <= gate;
    }

    private double random01(long nodeHash, int salt) {
        long mixed = nodeHash ^ (CONST_X * (salt + 1L)) ^ (CONST_Z * (salt + 7L));
        return (mix(mixed) >>> 11) * 0x1.0p-53;
    }

}
