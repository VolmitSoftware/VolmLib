package art.arcane.volmlib.util.bukkit;

import org.bukkit.NamespacedKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
}
