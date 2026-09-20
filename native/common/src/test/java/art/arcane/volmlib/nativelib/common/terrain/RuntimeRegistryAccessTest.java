package art.arcane.volmlib.nativelib.common.terrain;

import art.arcane.volmlib.nativelib.terrain.WorldRuntimeExecution;
import org.junit.Test;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RuntimeRegistryAccessTest {
    @Test
    public void generationRegistryAccessUsesTheFullServerRegistry() throws Exception {
        Object datapackDimensions = new Object();
        Object registryAccess = new Object();
        TestServer server = new TestServer(datapackDimensions, registryAccess);
        RuntimeCapabilities capabilities = mock(RuntimeCapabilities.class);
        when(capabilities.minecraftServer()).thenReturn(server);
        when(capabilities.serverRegistryAccessMethod()).thenReturn(
                RuntimeResolution.resolveServerRegistryAccessMethod(TestServer.class));
        when(capabilities.worldLoaderContextField()).thenReturn(
                RuntimeResolution.resolveField(TestServer.class, "worldLoaderContext"));
        RuntimeOperations operations = new RuntimeOperations(mock(WorldRuntimeExecution.class));
        assertSame(registryAccess, operations.getRuntimeServerRegistryAccess(capabilities));
        assertSame(datapackDimensions, operations.getRuntimeDatapackDimensions(capabilities));
        assertNotSame(operations.getRuntimeDatapackDimensions(capabilities), operations.getRuntimeServerRegistryAccess(capabilities));
    }

    private static final class TestServer {
        private final Context worldLoaderContext;
        private final Object registryAccess;

        private TestServer(Object dimensions, Object registryAccess) {
            this.worldLoaderContext = new Context(dimensions);
            this.registryAccess = registryAccess;
        }

        private Object registryAccess() {
            return registryAccess;
        }
    }

    private record Context(Object datapackDimensions) {
    }
    @Test
    public void selectsRegistryOverloadThatAcceptsTheNativeKey() throws Exception {
        assertEquals("stem", RuntimeOperations.lookupRegistryValue(new OverloadedRegistry(), new Key("world")));
    }

    @Test
    public void unwrapsHolderWhenOnlyTheTypedGetOverloadIsPresent() throws Exception {
        assertEquals("fallback-stem", RuntimeOperations.lookupRegistryValue(new HolderRegistry(), new Key("world")));
    }

    public record Key(String value) {
    }

    public record Holder(String value) {
    }

    public static final class OverloadedRegistry {
        public String getValue(String identifier) {
            throw new AssertionError("Identifier overload must not receive a registry key");
        }

        public String getValue(Key key) {
            return "stem";
        }
    }

    public static final class HolderRegistry {
        public String get(String identifier) {
            throw new AssertionError("Identifier overload must not receive a registry key");
        }

        public Optional<Holder> get(Key key) {
            return Optional.of(new Holder("fallback-stem"));
        }
    }

    @Test
    public void resolvesDefaultStemFromConfiguredDimensions() throws Exception {
        SettingsCookie cookie = new SettingsCookie(new GenerationSettings(new HolderRegistry()));
        assertEquals("fallback-stem", RuntimeOperations.resolveConfiguredLevelStem(cookie, new Key("world")));
    }

    public record SettingsCookie(GenerationSettings genSettings) {
    }

    public record GenerationSettings(HolderRegistry dimensions) {
    }

}
