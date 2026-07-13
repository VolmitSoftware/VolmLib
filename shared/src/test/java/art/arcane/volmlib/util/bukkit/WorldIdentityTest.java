package art.arcane.volmlib.util.bukkit;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class WorldIdentityTest {
    @Test
    public void parse_requiresFullyQualifiedKey() {
        assertEquals(NamespacedKey.minecraft("overworld"), WorldIdentity.parse("minecraft:overworld"));
        assertEquals(new NamespacedKey("iris", "floating_islands"), WorldIdentity.parse(" iris:floating_islands "));
        assertThrows(IllegalArgumentException.class, () -> WorldIdentity.parse("world"));
        assertThrows(IllegalArgumentException.class, () -> WorldIdentity.parse(":world"));
        assertThrows(IllegalArgumentException.class, () -> WorldIdentity.parse("minecraft:"));
        assertThrows(IllegalArgumentException.class, () -> WorldIdentity.parse("Bad Namespace:world"));
    }

    @Test
    public void key_usesBukkitKeyedWorldContract() {
        NamespacedKey key = new NamespacedKey("wormholes", "pockets");
        World world = world("pockets", key);

        assertEquals(key, WorldIdentity.key(world));
        assertEquals("wormholes:pockets", WorldIdentity.serialize(world));
    }

    @Test
    public void resolve_matchesWorldKeyAndReturnsEmptyWhenAbsent() {
        World overworld = world("world", NamespacedKey.minecraft("overworld"));
        World pockets = world("pockets", new NamespacedKey("wormholes", "pockets"));

        assertSame(pockets, WorldIdentity.resolve(new NamespacedKey("wormholes", "pockets"), List.of(overworld, pockets)).orElseThrow());
        assertFalse(WorldIdentity.resolve(new NamespacedKey("wormholes", "missing"), List.of(overworld, pockets)).isPresent());
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
}
