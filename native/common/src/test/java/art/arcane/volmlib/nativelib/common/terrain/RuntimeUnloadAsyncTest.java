package art.arcane.volmlib.nativelib.common.terrain;

import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuntimeUnloadAsyncTest {
    @Test
    public void manualUnloadFallbackDispatchesWorldUnloadEvent() {
        World world = mock(World.class);
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            assertTrue(RuntimeOperations.announceManualWorldUnload(world));
        }

        verify(pluginManager).callEvent(any(WorldUnloadEvent.class));
    }

    @Test
    public void manualUnloadFallbackHonorsCancelledWorldUnloadEvent() {
        World world = mock(World.class);
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            WorldUnloadEvent event = invocation.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(any(WorldUnloadEvent.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            assertFalse(RuntimeOperations.announceManualWorldUnload(world));
        }
    }

    @Test
    public void reflectedAsyncUnloadWaitsForTrueCallback() throws Exception {
        CallbackServer server = new CallbackServer();
        Method unloadMethod = CallbackServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = RuntimeOperations.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                false
        );

        assertFalse(result.isDone());
        server.complete(true);
        assertTrue(result.join());
    }

    @Test
    public void reflectedAsyncUnloadWaitsForFalseCallback() throws Exception {
        CallbackServer server = new CallbackServer();
        Method unloadMethod = CallbackServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = RuntimeOperations.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                true
        );

        assertFalse(result.isDone());
        server.complete(false);
        assertFalse(result.join());
    }

    @Test
    public void reflectedAsyncUnloadAcceptsCanvasResultCallback() throws Exception {
        CanvasCallbackServer server = new CanvasCallbackServer();
        Method unloadMethod = CanvasCallbackServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = RuntimeOperations.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                true
        );

        assertFalse(result.isDone());
        server.complete(CanvasUnloadResult.SUCCESS);
        assertTrue(result.join());
    }

    @Test
    public void reflectedAsyncUnloadMapsCanvasFailureResultToFalse() throws Exception {
        CanvasCallbackServer server = new CanvasCallbackServer();
        Method unloadMethod = CanvasCallbackServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = RuntimeOperations.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                false
        );

        server.complete(CanvasUnloadResult.FAILURE);
        assertFalse(result.join());
    }

    @Test
    public void reflectedAsyncUnloadPropagatesInvocationFailure() throws Exception {
        FailingServer server = new FailingServer();
        Method unloadMethod = FailingServer.class.getMethod(
                "unloadWorldAsync",
                World.class,
                boolean.class,
                Consumer.class
        );

        CompletableFuture<Boolean> result = RuntimeOperations.invokeAsyncUnload(
                server,
                unloadMethod,
                mock(World.class),
                false
        );

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertEquals("unload failed", failure.getCause().getMessage());
    }

    public static final class CallbackServer {
        private Consumer<Boolean> callback;

        public void unloadWorldAsync(World world, boolean save, Consumer<Boolean> callback) {
            this.callback = callback;
        }

        private void complete(boolean unloaded) {
            callback.accept(unloaded);
        }
    }

    public static final class FailingServer {
        public void unloadWorldAsync(World world, boolean save, Consumer<Boolean> callback) {
            throw new IllegalStateException("unload failed");
        }
    }

    public static final class CanvasCallbackServer {
        private Consumer<CanvasUnloadResult> callback;

        public void unloadWorldAsync(World world, boolean save, Consumer<CanvasUnloadResult> callback) {
            this.callback = callback;
        }

        private void complete(CanvasUnloadResult result) {
            callback.accept(result);
        }
    }

    public enum CanvasUnloadResult {
        SUCCESS,
        FAILURE;

        public boolean isSuccess() {
            return this == SUCCESS;
        }
    }

}
