package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecyclePolicies;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecyclePolicy;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeWorldLifecycleTest {
    private static Path bridgePath;
    private static JarFile bridgeArchive;
    private static Class<?> systemBridge;

    @BeforeClass
    public static void installBridge() throws Exception {
        bridgePath = Files.createTempFile("native-lifecycle-bridge", ".jar");
        String resource = CloseBridge.class.getName().replace('.', '/') + ".class";
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(bridgePath));
             InputStream input = CloseBridge.class.getClassLoader().getResourceAsStream(resource)) {
            output.putNextEntry(new JarEntry(resource));
            input.transferTo(output);
            output.closeEntry();
        }
        bridgeArchive = new JarFile(bridgePath.toFile());
        ByteBuddyAgent.install().appendToSystemClassLoaderSearch(bridgeArchive);
        systemBridge = Class.forName(CloseBridge.class.getName(), true, ClassLoader.getSystemClassLoader());
    }

    @AfterClass
    public static void removeBridgeArchive() throws Exception {
        bridgeArchive.close();
        Files.delete(bridgePath);
    }

    @Test
    public void startupProtectionKeepsUnloadedClassesAndResourcesUntilRelease() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.lifecycle.initializeLifecycle(fixture.loader);
            fixture.lifecycle.initializeLifecycle(fixture.loader);
            assertNull(field(fixture.lifecycle, "levelStorageAccessTransformer"));
            assertNull(field(fixture.lifecycle, "serverLevelTransformer"));
            assertNull(field(fixture.lifecycle, "registryPacketTransformer"));
            fixture.lifecycle.deferPluginClassLoaderClose();
            fixture.lifecycle.deferPluginClassLoaderClose();
            fixture.loader.close();
            fixture.loader.close();
            assertEquals(0, fixture.loader.closes);
            assertSame(fixture.loader, fixture.loader.loadClass("fixture.DeferredCleanup").getClassLoader());
            try (InputStream resource = fixture.loader.getResourceAsStream("late-cleanup.txt")) {
                assertNotNull(resource);
                assertEquals("available", new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }
            fixture.lifecycle.releasePluginClassLoaderClose();
            fixture.lifecycle.releasePluginClassLoaderClose();
            assertEquals(1, fixture.loader.closes);
            assertNull(fixture.loader.getResourceAsStream("late-cleanup.txt"));
            assertThrows(ClassNotFoundException.class, () -> fixture.loader.loadClass("fixture.AfterRelease"));
            verify(fixture.policy, times(1)).retainClassLoader(fixture.loader);
            verify(fixture.policy, times(1)).releaseClassLoader(fixture.loader);
        }
    }

    @Test
    public void normalDisableRemovesProtectionWithoutClosingAnUnrequestedLoader() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.lifecycle.initializeLifecycle(fixture.loader);
            fixture.lifecycle.releasePluginClassLoaderClose();
            fixture.lifecycle.releasePluginClassLoaderClose();
            assertEquals(0, fixture.loader.closes);
            assertNotNull(fixture.loader.getResource("late-cleanup.txt"));
            verify(fixture.policy, times(1)).releaseClassLoader(fixture.loader);
            fixture.loader.close();
            assertEquals(1, fixture.loader.closes);
        }
    }

    @Test
    public void protectionRejectsMissingInitializationAndAnotherLoader() throws Exception {
        try (Fixture fixture = new Fixture(); TrackingLoader other = new TrackingLoader(new URL[0])) {
            assertThrows(IllegalStateException.class, fixture.lifecycle::deferPluginClassLoaderClose);
            fixture.lifecycle.initializeLifecycle(fixture.loader);
            assertThrows(IllegalStateException.class, () -> fixture.lifecycle.initializeLifecycle(other));
            fixture.lifecycle.deferPluginClassLoaderClose();
            other.close();
            fixture.loader.close();
            assertEquals(1, other.closes);
            assertEquals(0, fixture.loader.closes);
        }
    }

    @Test
    public void failedInitializationUnwindsAndCanRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Instrumentation unavailable = mock(Instrumentation.class, delegatesTo(fixture.instrumentation));
            when(unavailable.getAllLoadedClasses()).thenReturn(new Class<?>[0]);
            when(fixture.policy.instrumentation()).thenReturn(unavailable);
            assertThrows(IllegalStateException.class, () -> fixture.lifecycle.initializeLifecycle(fixture.loader));
            assertNull(field(fixture.lifecycle, "protectedPluginClassLoader"));
            assertNull(field(fixture.lifecycle, "pluginClassLoaderTransformer"));
            assertNull(field(fixture.lifecycle, "serverStorageCloseTransformer"));
            verify(unavailable).removeTransformer(any());
            assertThrows(IllegalStateException.class, fixture.lifecycle::deferPluginClassLoaderClose);
            doReturn(fixture.instrumentation).when(fixture.policy).instrumentation();
            fixture.lifecycle.initializeLifecycle(fixture.loader);
            fixture.lifecycle.deferPluginClassLoaderClose();
            fixture.loader.close();
            assertEquals(0, fixture.loader.closes);
        }
    }

    @Test
    public void failedLoaderProtectionUnwindsAnInstalledStorageBoundary() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Object storage = new Object();
            AtomicBoolean closed = (AtomicBoolean) systemBridge.getMethod("trackServerStorage", Object.class)
                    .invoke(null, storage);
            ResettableClassFileTransformer storageTransformer = mock(ResettableClassFileTransformer.class);
            Instrumentation unavailable = mock(Instrumentation.class, delegatesTo(fixture.instrumentation));
            when(unavailable.getAllLoadedClasses()).thenReturn(new Class<?>[0]);
            when(storageTransformer.reset(unavailable, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)).thenReturn(true);
            setField(fixture.lifecycle, "trackedServerStorage", storage);
            setField(fixture.lifecycle, "serverStorageClosed", closed);
            setField(fixture.lifecycle, "serverStorageCloseTransformer", storageTransformer);
            when(fixture.policy.instrumentation()).thenReturn(unavailable);
            assertThrows(IllegalStateException.class, () -> fixture.lifecycle.initializeLifecycle(fixture.loader));
            verify(storageTransformer).reset(unavailable, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
            assertEquals(false, systemBridge.getMethod("isStorageTracked", Object.class).invoke(null, storage));
            assertNull(field(fixture.lifecycle, "trackedServerStorage"));
            assertNull(field(fixture.lifecycle, "serverStorageClosed"));
            assertNull(field(fixture.lifecycle, "serverStorageCloseTransformer"));
        }
    }

    @Test
    public void worldInjectionFailureAndRollbackLeaveShutdownProtectionInstalled() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.lifecycle.initializeLifecycle(fixture.loader);
            when(fixture.policy.instrumentation()).thenReturn(fixture.instrumentation)
                    .thenThrow(new IllegalStateException("world injection failed"))
                    .thenReturn(fixture.instrumentation);
            assertFalse(fixture.lifecycle.injectBukkit());
            assertNull(NativeWorldLifecyclePolicies.policy("LifecycleTest"));
            assertNull(field(fixture.lifecycle, "levelStorageAccessTransformer"));
            assertNull(field(fixture.lifecycle, "serverLevelTransformer"));
            doReturn(fixture.instrumentation).when(fixture.policy).instrumentation();
            fixture.lifecycle.uninjectBukkit();
            fixture.lifecycle.deferPluginClassLoaderClose();
            fixture.loader.close();
            assertEquals(0, fixture.loader.closes);
            assertNotNull(fixture.loader.getResource("late-cleanup.txt"));
        }
    }

    @Test
    public void failedTransformerCleanupStillReleasesTheExactRetainedLoaderOnce() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.lifecycle.initializeLifecycle(fixture.loader);
            fixture.lifecycle.deferPluginClassLoaderClose();
            fixture.loader.close();
            ResettableClassFileTransformer installed = (ResettableClassFileTransformer) field(fixture.lifecycle, "pluginClassLoaderTransformer");
            ResettableClassFileTransformer failing = mock(ResettableClassFileTransformer.class);
            setField(fixture.lifecycle, "pluginClassLoaderTransformer", failing);
            IllegalStateException releaseFailure = new IllegalStateException("loader close failed");
            doAnswer(invocation -> {
                systemBridge.getMethod("release", ClassLoader.class)
                        .invoke(null, invocation.getArgument(0, ClassLoader.class));
                throw releaseFailure;
            }).when(fixture.policy).releaseClassLoader(fixture.loader);
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        fixture.lifecycle::releasePluginClassLoaderClose);
                assertEquals(1, failure.getCause().getSuppressed().length);
                assertSame(releaseFailure, failure.getCause().getSuppressed()[0]);
                fixture.lifecycle.releasePluginClassLoaderClose();
                assertEquals(1, fixture.loader.closes);
                verify(fixture.policy, times(1)).releaseClassLoader(fixture.loader);
            } finally {
                assertTrue(installed.reset(fixture.instrumentation, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION));
            }
        }
    }

    private static Object field(NativeWorldLifecycle lifecycle, String name) throws ReflectiveOperationException {
        Field field = NativeWorldLifecycle.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(lifecycle);
    }

    private static void setField(NativeWorldLifecycle lifecycle, String name, Object value) throws ReflectiveOperationException {
        Field field = NativeWorldLifecycle.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(lifecycle, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final Instrumentation instrumentation = ByteBuddyAgent.install();
        private final NativeWorldLifecyclePolicy policy = mock(NativeWorldLifecyclePolicy.class);
        private final NativeWorldLifecycle lifecycle = new NativeWorldLifecycle(policy, "fixture.Generator");
        private final Path jar = Files.createTempFile("native-lifecycle", ".jar");
        private final TrackingLoader loader;

        private Fixture() throws Exception {
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                for (String name : new String[]{"DeferredCleanup", "AfterRelease"}) {
                    output.putNextEntry(new JarEntry("fixture/" + name + ".class"));
                    output.write(new ByteBuddy().subclass(Object.class).name("fixture." + name).make().getBytes());
                    output.closeEntry();
                }
                output.putNextEntry(new JarEntry("late-cleanup.txt"));
                output.write("available".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
            loader = new TrackingLoader(new URL[]{jar.toUri().toURL()});
            when(policy.pluginName()).thenReturn("LifecycleTest");
            when(policy.classLoaderLifecycleBridgeName()).thenReturn(systemBridge.getName());
            when(policy.instrumentation()).thenReturn(instrumentation);
            doAnswer(invocation -> {
                systemBridge.getMethod("retain", ClassLoader.class).invoke(null, invocation.getArgument(0, ClassLoader.class));
                return null;
            }).when(policy).retainClassLoader(any());
            doAnswer(invocation -> {
                systemBridge.getMethod("release", ClassLoader.class).invoke(null, invocation.getArgument(0, ClassLoader.class));
                return null;
            }).when(policy).releaseClassLoader(any());
        }

        @Override
        public void close() throws Exception {
            lifecycle.releasePluginClassLoaderClose();
            loader.close();
            Files.delete(jar);
        }
    }

    public static final class CloseBridge {
        private static final Map<ClassLoader, Boolean> RETAINED = new IdentityHashMap<>();
        private static final Map<Object, AtomicBoolean> STORAGE = new IdentityHashMap<>();

        public static synchronized AtomicBoolean trackServerStorage(Object storage) {
            AtomicBoolean closed = new AtomicBoolean();
            STORAGE.put(storage, closed);
            return closed;
        }

        public static synchronized void releaseServerStorage(Object storage) {
            STORAGE.remove(storage);
        }

        public static synchronized boolean isStorageTracked(Object storage) {
            return STORAGE.containsKey(storage);
        }

        public static synchronized boolean deferClassLoaderClose(ClassLoader loader) {
            if (!RETAINED.containsKey(loader)) {
                return false;
            }
            RETAINED.put(loader, true);
            return true;
        }

        public static synchronized void retain(ClassLoader loader) {
            RETAINED.putIfAbsent(loader, false);
        }

        public static void release(ClassLoader loader) throws IOException {
            boolean requested;
            synchronized (CloseBridge.class) {
                requested = Boolean.TRUE.equals(RETAINED.remove(loader));
            }
            if (requested) {
                ((URLClassLoader) loader).close();
            }
        }
    }

    public static final class TrackingLoader extends URLClassLoader {
        private int closes;

        private TrackingLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        public void close() throws IOException {
            closes++;
            super.close();
        }
    }
}
