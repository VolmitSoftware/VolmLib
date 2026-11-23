package art.arcane.volmlib.nativelib.terrain.feature;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeStepwiseFeaturePassTest {
    @Test
    public void advancingThroughAStepPlacesEveryEarlierStepOnceInOrder() {
        RecordingPass pass = new RecordingPass(11);

        pass.placeThroughStep(2);
        pass.placeThroughStep(1);
        pass.placeThroughStep(3);

        assertEquals(List.of(0, 1, 2, 3), pass.placed);
    }

    @Test
    public void remainingStepsFinishThePassAfterTheLastStructureStep() {
        RecordingPass pass = new RecordingPass(13);

        pass.placeThroughStep(10);
        assertFalse(pass.placedAllSteps());
        pass.placeRemainingSteps();
        pass.placeRemainingSteps();

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), pass.placed);
        assertTrue(pass.placedAllSteps());
    }

    @Test
    public void stepsBeyondTheFeatureTableAreIgnored() {
        RecordingPass pass = new RecordingPass(3);

        pass.placeThroughStep(10);

        assertEquals(List.of(0, 1, 2), pass.placed);
    }

    @Test
    public void aFailedStepIsNotPlacedAgain() {
        RecordingPass pass = new RecordingPass(4);
        pass.failingStep = 1;

        assertThrows(IllegalStateException.class, () -> pass.placeThroughStep(2));
        pass.failingStep = -1;
        pass.placeRemainingSteps();

        assertEquals(List.of(0, 1, 2, 3), pass.placed);
    }

    @Test
    public void theInertPassPlacesNothing() {
        NativeChunkFeaturePass.INERT.placeThroughStep(10);
        NativeChunkFeaturePass.INERT.placeRemainingSteps();

        assertFalse(NativeChunkFeaturePass.INERT.active());
    }

    private static final class RecordingPass extends NativeStepwiseFeaturePass {
        private final List<Integer> placed = new ArrayList<>();
        private int failingStep = -1;

        private RecordingPass(int stepCount) {
            super(stepCount);
        }

        @Override
        protected void placeStep(int step) {
            placed.add(step);
            if (step == failingStep) {
                throw new IllegalStateException("step " + step + " failed");
            }
        }

        @Override
        public void close() {
        }
    }
}
