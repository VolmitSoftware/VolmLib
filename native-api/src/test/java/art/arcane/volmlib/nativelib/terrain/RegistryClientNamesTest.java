package art.arcane.volmlib.nativelib.terrain;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RegistryClientNamesTest {
    @Test
    public void unregisteredKeysKeepTheirNamesAndOrder() {
        List<String> keys = List.of("minecraft:plains", "iris:biomes/physical");

        assertEquals(keys, RegistryClientNames.resolve("test:unregistered", keys));
    }

    @Test
    public void registeredNamesReplaceOnlyTheirOriginalPositions() {
        RegistryClientNames.register("test:ordered", "iris:biomes/forest", "iris:overworld/forest");
        List<String> keys = List.of("minecraft:plains", "iris:biomes/forest", "minecraft:desert");

        List<String> resolved = RegistryClientNames.resolve("test:ordered", keys);

        assertEquals(List.of("minecraft:plains", "iris:overworld/forest", "minecraft:desert"), resolved);
        assertThrows(UnsupportedOperationException.class, () -> resolved.add("minecraft:swamp"));
    }

    @Test
    public void competingDefinitionsUseDeterministicNumberedNamespaces() {
        RegistryClientNames.register("test:competing", "iris:biomes/z", "iris:overworld/forest");
        RegistryClientNames.register("test:competing", "iris:biomes/a", "iris:overworld/forest");
        RegistryClientNames.register("test:competing", "iris:biomes/m", "iris:overworld/forest");

        assertEquals(List.of("iris_3:overworld/forest", "iris:overworld/forest", "iris_2:overworld/forest"),
                RegistryClientNames.resolve("test:competing",
                        List.of("iris:biomes/z", "iris:biomes/a", "iris:biomes/m")));
        assertEquals(List.of("iris_2:overworld/forest", "iris_3:overworld/forest", "iris:overworld/forest"),
                RegistryClientNames.resolve("test:competing",
                        List.of("iris:biomes/m", "iris:biomes/z", "iris:biomes/a")));
    }

    @Test
    public void unchangedKeysAndOtherDesiredBasesRemainReserved() {
        RegistryClientNames.register("test:reserved", "iris:biomes/a", "iris:overworld/forest");
        RegistryClientNames.register("test:reserved", "iris:biomes/b", "iris:overworld/forest");
        RegistryClientNames.register("test:reserved", "iris:biomes/c", "iris_2:overworld/forest");
        List<String> keys = List.of("iris:biomes/b", "iris:overworld/forest", "iris:biomes/c",
                "iris_3:overworld/forest", "iris:biomes/a");

        assertEquals(List.of("iris_5:overworld/forest", "iris:overworld/forest", "iris_2:overworld/forest",
                        "iris_3:overworld/forest", "iris_4:overworld/forest"),
                RegistryClientNames.resolve("test:reserved", keys));
    }

    @Test
    public void registrationIsIdempotentButCannotChangeAnExistingName() {
        RegistryClientNames.register("test:immutable", "iris:biomes/a", "iris:overworld/forest");
        RegistryClientNames.register("test:immutable", "iris:biomes/a", "iris:overworld/forest");

        assertThrows(IllegalArgumentException.class, () -> RegistryClientNames.register(
                "test:immutable", "iris:biomes/a", "iris:overworld/swamp"));
        assertEquals(List.of("iris:overworld/forest"),
                RegistryClientNames.resolve("test:immutable", List.of("iris:biomes/a")));
        assertEquals(List.of("iris:biomes/a"),
                RegistryClientNames.resolve("test:other_registry", List.of("iris:biomes/a")));
    }

    @Test
    public void registrationRequiresCanonicalResourceKeys() {
        assertThrows(IllegalArgumentException.class, () -> RegistryClientNames.register(
                "biome", "iris:biomes/a", "iris:overworld/forest"));
        assertThrows(IllegalArgumentException.class, () -> RegistryClientNames.register(
                "test:invalid", "iris:Biomes/a", "iris:overworld/forest"));
        assertThrows(IllegalArgumentException.class, () -> RegistryClientNames.register(
                "test:invalid", "iris:biomes/a", "iris:overworld/Forest"));
        assertThrows(NullPointerException.class, () -> RegistryClientNames.register(
                "test:invalid", null, "iris:overworld/forest"));
    }
}
