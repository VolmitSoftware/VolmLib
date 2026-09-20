package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeatureControl;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeaturePolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeImportedFeatureStageTest {
    @Test
    @SuppressWarnings("unchecked")
    public void disabledTableTracksDimensionIdentityAndRuntimeEvictionWithoutReadingRegistries() {
        NativeImportedFeaturePolicy<Dimension> policy = mock(NativeImportedFeaturePolicy.class);
        NativeImportedFeatureControl control = mock(NativeImportedFeatureControl.class);
        when(policy.control()).thenReturn(control);
        when(policy.runtimeId()).thenReturn(7);
        when(policy.dimension()).thenReturn(new Dimension("same-key"));
        NativeImportedFeatureStage<Dimension> stage = new NativeImportedFeatureStage<>(policy);

        stage.prepare(null);
        stage.prepare(null);
        assertNull(stage.generationSettings(null));
        verify(policy, times(1)).control();

        when(policy.dimension()).thenReturn(new Dimension("same-key"));
        stage.prepare(null);
        verify(policy, times(2)).control();

        stage.evictRuntime(7);
        stage.prepare(null);
        verify(policy, times(3)).control();
        verify(policy, never()).visibleBiomeKeys();
        verify(policy, never()).placement();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void simultaneousChunkPreparationBuildsTheRuntimeStateOnce() throws Exception {
        NativeImportedFeaturePolicy<Dimension> policy = mock(NativeImportedFeaturePolicy.class);
        when(policy.control()).thenReturn(mock(NativeImportedFeatureControl.class));
        when(policy.dimension()).thenReturn(new Dimension("shared"));
        NativeImportedFeatureStage<Dimension> stage = new NativeImportedFeatureStage<>(policy);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Chunk preparation did not start");
                    }
                    stage.prepare(null);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            verify(policy, times(1)).control();
        } finally {
            executor.shutdownNow();
        }
    }

    private record Dimension(String name) {
    }
}
