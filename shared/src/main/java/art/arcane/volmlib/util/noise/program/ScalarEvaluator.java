package art.arcane.volmlib.util.noise.program;

abstract class ScalarEvaluator {
    private static final ThreadLocal<NoiseScratch> SCRATCH = ThreadLocal.withInitial(NoiseScratch::new);

    static ScalarEvaluator of(NoiseProgram program) {
        PlanarChain chain = program.volumetric ? null : PlanarChain.tryCreate(program);
        return chain != null ? chain : new Interpreted(program);
    }

    abstract double sample(double x, double y, double z);

    private static final class Interpreted extends ScalarEvaluator {
        private final NoiseProgram program;

        private Interpreted(NoiseProgram program) {
            this.program = program;
        }

        @Override
        double sample(double x, double y, double z) {
            NoiseScratch scratch = SCRATCH.get();
            if (scratch.scalarActive) {
                return JavaScalarBackend.evaluate(program, new double[program.termCount], new double[]{x, y, z});
            }
            scratch.scalarActive = true;
            try {
                double[] coordinates = scratch.scalarCoordinates;
                coordinates[0] = x;
                coordinates[1] = y;
                coordinates[2] = z;
                return JavaScalarBackend.evaluate(program, scratch.scalarValues(program.termCount), coordinates);
            } finally {
                scratch.scalarActive = false;
            }
        }
    }

    /**
     * A planar program that is a plain chain of fractures: below the root, every level is one term per axis, and
     * both terms are warped by the same pair of terms on the level below.
     */
    private static final class PlanarChain extends ScalarEvaluator {
        private static final int TERMS_PER_LEVEL = 2;

        private final PlanarStep root;
        private final PlanarStep[] firstAxis;
        private final PlanarStep[] secondAxis;

        private PlanarChain(PlanarStep root, PlanarStep[] firstAxis, PlanarStep[] secondAxis) {
            this.root = root;
            this.firstAxis = firstAxis;
            this.secondAxis = secondAxis;
        }

        private static PlanarChain tryCreate(NoiseProgram program) {
            int depth = fractureDepth(program);
            if (depth < 0 || program.termCount != 1 + (TERMS_PER_LEVEL * depth)) {
                return null;
            }
            PlanarStep[] firstAxis = new PlanarStep[depth];
            PlanarStep[] secondAxis = new PlanarStep[depth];
            int term = program.rootTerm;
            for (int level = 0; level < depth; level++) {
                firstAxis[level] = new PlanarStep(program, program.termDep0[term]);
                secondAxis[level] = new PlanarStep(program, program.termDep1[term]);
                term = program.termDep0[term];
            }
            return new PlanarChain(new PlanarStep(program, program.rootTerm), firstAxis, secondAxis);
        }

        private static int fractureDepth(NoiseProgram program) {
            int term = program.rootTerm;
            if (!isPlainPlanarTerm(program, term)) {
                return -1;
            }
            int depth = 0;
            while (program.termDep0[term] >= 0) {
                int first = program.termDep0[term];
                int second = program.termDep1[term];
                if (!isPlainPlanarTerm(program, first) || !isPlainPlanarTerm(program, second)
                        || program.termDep0[first] != program.termDep0[second]
                        || program.termDep1[first] != program.termDep1[second]) {
                    return -1;
                }
                depth++;
                term = first;
            }
            return depth;
        }

        private static boolean isPlainPlanarTerm(NoiseProgram program, int term) {
            int node = program.termNode[term];
            return program.termMode[term] == NoiseProgram.UNSIGNED_2D
                    && program.termAxis0[term] == 0
                    && program.termAxis1[term] == 1
                    && !program.nodeOpaque[node]
                    && program.nodeChildren[node] == null;
        }

        @Override
        double sample(double x, double z, double ignored) {
            int leaf = firstAxis.length - 1;
            if (leaf < 0) {
                return root.sample(x, z);
            }
            double first = firstAxis[leaf].sample(x, z);
            double second = secondAxis[leaf].sample(x, z);
            for (int level = leaf - 1; level >= 0; level--) {
                double nextFirst = firstAxis[level].sampleWarped(x, z, first, second);
                double nextSecond = secondAxis[level].sampleWarped(x, z, first, second);
                first = nextFirst;
                second = nextSecond;
            }
            return root.sampleWarped(x, z, first, second);
        }
    }

    private static final class PlanarStep {
        private final PrimitiveKernel kernel;
        private final double scale;
        private final double fractureScale;
        private final double opacity;
        private final boolean post;
        private final double power;
        private final double down;
        private final double up;
        private final double patch;

        private PlanarStep(NoiseProgram program, int term) {
            int node = program.termNode[term];
            kernel = program.nodeKernels[node];
            scale = program.nodeScale[node];
            fractureScale = program.nodeFractureScale[node];
            opacity = program.nodeOpacity[node];
            post = program.termApplyPost[term];
            power = program.nodePower[node];
            down = program.nodeDown[node];
            up = program.nodeUp[node];
            patch = program.nodePatch[node];
        }

        private double sample(double x, double z) {
            return finish(kernel.noise2(x * scale, z * scale) * opacity);
        }

        private double sampleWarped(double x, double z, double firstWarp, double secondWarp) {
            return finish(kernel.noise2((x + ((firstWarp - 0.5D) * fractureScale)) * scale,
                    (z + ((secondWarp - 0.5D) * fractureScale)) * scale) * opacity);
        }

        private double finish(double raw) {
            if (!post) {
                return raw;
            }
            double n = power != 1D ? (raw < 0 ? -Math.pow(Math.abs(raw), power) : Math.pow(raw, power)) : raw;
            return ((n / 1D) - down + up) * patch;
        }
    }
}
