package art.arcane.volmlib.util.data;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CuboidTest {
    @Test
    public void serializedCuboid_usesNamespacedWorldKey() {
        Cuboid cuboid = new Cuboid(Map.of(
                "worldKey", "minecraft:overworld",
                "x1", 1,
                "y1", 2,
                "z1", 3,
                "x2", 4,
                "y2", 5,
                "z2", 6));

        assertEquals("minecraft:overworld", cuboid.serialize().get("worldKey"));
    }

    @Test
    public void serializedCuboid_rejectsLegacyWorldNameIdentity() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("worldName", "world");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new Cuboid(legacy));
        assertEquals("Cuboid requires a worldKey", exception.getMessage());
    }
}
