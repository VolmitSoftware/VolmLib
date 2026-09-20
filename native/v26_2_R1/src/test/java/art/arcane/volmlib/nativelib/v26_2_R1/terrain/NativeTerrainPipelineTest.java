package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRoute;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainPipelinePolicy;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeTerrainPipelineTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void pendingGenerationRetainsResourcesUntilFailure() {
        Fixture fixture = new Fixture();
        CompletableFuture<ChunkAccess> result = fixture.start();
        assertFalse(result.isDone());
        verify(fixture.lease).detachThread();
        verify(fixture.route).detachThread();
        verify(fixture.lease, never()).close();
        IllegalStateException failure = new IllegalStateException("generation failed");
        fixture.delegateResult.completeExceptionally(failure);
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        fixture.assertClosedInOrder();
    }

    @Test
    public void delegateCancellationClosesResourcesAndRemainsCancellation() {
        Fixture fixture = new Fixture();
        CompletableFuture<ChunkAccess> result = fixture.start();
        fixture.delegateResult.cancel(false);
        assertTrue(result.isCancelled());
        fixture.assertClosedInOrder();
    }

    @Test
    public void cleanupFailuresDoNotPreventRemainingResourcesClosing() {
        Fixture fixture = new Fixture();
        IllegalStateException leaseFailure = new IllegalStateException("lease failed");
        IllegalStateException routeFailure = new IllegalStateException("route failed");
        doThrow(leaseFailure).when(fixture.lease).close();
        doThrow(routeFailure).when(fixture.route).close();
        CompletableFuture<ChunkAccess> result = fixture.start();
        IllegalStateException failure = new IllegalStateException("generation failed");
        fixture.delegateResult.completeExceptionally(failure);
        CompletionException completion = assertThrows(CompletionException.class, result::join);
        assertSame(failure, completion.getCause());
        assertSame(leaseFailure, completion.getSuppressed()[0]);
        assertSame(routeFailure, completion.getSuppressed()[1]);
        fixture.assertClosedInOrder();
    }

    @Test
    public void routeAdmissionFailureReleasesStageWithoutAcquiringLease() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("route unavailable");
        when(fixture.policy.openRoute(2, 3, "bukkit_nms_chunk_pipeline")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, fixture::start));
        verify(fixture.stage).close();
        verify(fixture.policy, never()).acquireLease(anyString());
    }

    private static final class Fixture {
        private final ChunkGenerator delegate = mock(ChunkGenerator.class);
        private final ChunkAccess chunk = mock(ChunkAccess.class);
        private final NativeGenerationScope stage = mock(NativeGenerationScope.class);
        private final NativeGenerationLease lease = mock(NativeGenerationLease.class);
        private final NativeGenerationRoute route = mock(NativeGenerationRoute.class);
        private final CompletableFuture<ChunkAccess> delegateResult = new CompletableFuture<>();
        private final NativeTerrainPipelinePolicy<NativeGenerationRoute> policy;
        private final NativeTerrainPipeline<NativeGenerationRoute> pipeline;

        @SuppressWarnings("unchecked")
        private Fixture() {
            policy = mock(NativeTerrainPipelinePolicy.class);
            when(chunk.getPos()).thenReturn(new ChunkPos(2, 3));
            when(policy.acquireStage(anyString())).thenReturn(stage);
            when(policy.acquireLease(anyString())).thenReturn(lease);
            when(policy.openRoute(2, 3, "bukkit_nms_chunk_pipeline")).thenReturn(route);
            when(delegate.fillFromNoise(null, null, null, chunk)).thenReturn(delegateResult);
            pipeline = new NativeTerrainPipeline<>(new NativeTerrainPipeline.Configuration<>(delegate, policy, null, -64));
        }

        private CompletableFuture<ChunkAccess> start() {
            return pipeline.fillFromNoise(null, null, null, chunk);
        }

        private void assertClosedInOrder() {
            InOrder order = inOrder(lease, route, stage);
            order.verify(lease).close();
            order.verify(route).close();
            order.verify(stage).close();
        }
    }
}
