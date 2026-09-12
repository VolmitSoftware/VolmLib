package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;

public class HexRandomSizeNoise extends HexagonalNoise implements OctaveNoise {
    private static final int MAX_DEPTH = 8;
    private static final double HEAT_SCALE = 18D;
    private static final double STRUCTURE_SCALE = 36D;
    private static final double SUBDIVIDE_BASE_THRESHOLD = 0.22D;
    private static final double SUBDIVIDE_LEVEL_STEP = 0.11D;
    private final long seed;
    private final SimplexNoise heat;
    private final SimplexNoise structure;

    public HexRandomSizeNoise(long seed) {
        RNG rng = new RNG(seed);
        this.seed = rng.lmax();
        this.heat = new SimplexNoise(rng.nextParallelRNG(221L).lmax());
        this.structure = new SimplexNoise(rng.nextParallelRNG(442L).lmax());
    }

    @Override
    public void setOctaves(int octaves) {
        heat.setOctaves(octaves);
        structure.setOctaves(octaves);
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

        while (level < MAX_DEPTH - 1 && shouldSubdivide(centerQ, centerR, nodeHash, level)) {
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
            nodeHash = mix(nodeHash ^ (CONST_Z * (childIndex + 1L)) ^ (CONST_X * (level + 1L)));
            level++;
        }

        double centerX = SQRT_3 * (centerQ + (centerR * 0.5D));
        double centerZ = 1.5D * centerR;
        double simplexValue = heat.noise(centerX * HEAT_SCALE, y * HEAT_SCALE, centerZ * HEAT_SCALE);
        return (simplexValue * 0.74D) + (random01(nodeHash, 11 + level) * 0.26D);
    }

    private boolean shouldSubdivide(double centerQ, double centerR, long nodeHash, int level) {
        double centerX = SQRT_3 * (centerQ + (centerR * 0.5D));
        double centerZ = 1.5D * centerR;
        double simplexValue = structure.noise(centerX * STRUCTURE_SCALE, centerZ * STRUCTURE_SCALE);
        double score = (simplexValue * 0.68D) + (random01(nodeHash, 71 + level) * 0.32D);
        return score > SUBDIVIDE_BASE_THRESHOLD + (level * SUBDIVIDE_LEVEL_STEP);
    }

    private double random01(long nodeHash, int salt) {
        long mixed = nodeHash ^ (CONST_X * (salt + 1L)) ^ (CONST_Z * (salt + 31L));
        return (mix(mixed) >>> 11) * 0x1.0p-53;
    }

}
