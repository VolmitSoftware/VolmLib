package art.arcane.volmlib.nativelib.terrain.feature;

/**
 * Places each feature step exactly once and in ascending order, however the caller advances it.
 */
public abstract class NativeStepwiseFeaturePass implements NativeChunkFeaturePass {
    private final int stepCount;
    private int nextStep;

    protected NativeStepwiseFeaturePass(int stepCount) {
        if (stepCount < 0) {
            throw new IllegalArgumentException("Feature step count cannot be negative");
        }
        this.stepCount = stepCount;
    }

    @Override
    public final boolean active() {
        return true;
    }

    @Override
    public final void placeThroughStep(int step) {
        int lastStep = Math.min(step, stepCount - 1);
        while (nextStep <= lastStep) {
            placeStep(nextStep++);
        }
    }

    @Override
    public final void placeRemainingSteps() {
        placeThroughStep(stepCount - 1);
    }

    protected final boolean placedAllSteps() {
        return nextStep >= stepCount;
    }

    protected abstract void placeStep(int step);
}
