package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class WorldHandlerBaseTest {
    @Test
    public void parse_resolvesExactBareWorldName() throws DirectorParsingException {
        World world = world("irisworld", NamespacedKey.minecraft("irisworld"));
        WorldHandlerBase handler = new TestWorldHandler(List.of(world));

        assertSame(world, handler.parse("irisworld", false));
        assertSame(world, handler.parse(" IRISWORLD ", false));
    }

    @Test
    public void parse_resolvesBarePathOfNamespacedWorld() throws DirectorParsingException {
        World world = world("iris_irisworld", new NamespacedKey("iris", "irisworld"));
        WorldHandlerBase handler = new TestWorldHandler(List.of(world));

        assertSame(world, handler.parse("irisworld", false));
        assertSame(world, handler.parse("iris:irisworld", false));
    }

    @Test
    public void parse_resolvesExactQualifiedWorldKey() throws DirectorParsingException {
        World minecraftWorld = world("irisworld", NamespacedKey.minecraft("irisworld"));
        World customWorld = world("skylands", new NamespacedKey("iris", "skylands"));
        WorldHandlerBase handler = new TestWorldHandler(List.of(minecraftWorld, customWorld));

        assertSame(minecraftWorld, handler.parse("minecraft:irisworld", false));
        assertSame(customWorld, handler.parse(" iris:skylands ", false));
    }

    @Test
    public void parse_rejectsPartialMissingAndMalformedWorldNames() {
        World world = world("world_nether", NamespacedKey.minecraft("world_nether"));
        WorldHandlerBase handler = new TestWorldHandler(List.of(world));

        assertThrows(DirectorParsingException.class, () -> handler.parse("world", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("missing", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("Bad Namespace:world", false));
    }

    @Test
    public void parse_rejectsAmbiguousBareWorldNames() {
        World first = world("irisworld", NamespacedKey.minecraft("irisworld"));
        World second = world("IRISWORLD", new NamespacedKey("iris", "irisworld"));
        WorldHandlerBase handler = new TestWorldHandler(List.of(first, second));

        assertThrows(DirectorParsingException.class, () -> handler.parse("irisworld", false));
    }

    private static World world(String name, NamespacedKey key) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> key;
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class TestWorldHandler extends WorldHandlerBase {
        private final List<World> worlds;

        private TestWorldHandler(List<World> worlds) {
            this.worlds = worlds;
        }

        @Override
        protected String excludedPrefix() {
            return "";
        }

        @Override
        protected List<World> worldOptions() {
            return worlds;
        }
    }
}
