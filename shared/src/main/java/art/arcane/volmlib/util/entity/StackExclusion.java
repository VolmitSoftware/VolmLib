package art.arcane.volmlib.util.entity;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class StackExclusion {
    private static final NamespacedKey KEY = new NamespacedKey("volmit", "no-stack");
    private static final byte FLAG = (byte) 1;

    private StackExclusion() {
    }

    public static void exclude(Entity entity) {
        if (entity == null) {
            return;
        }

        entity.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, FLAG);
    }

    public static void include(Entity entity) {
        if (entity == null) {
            return;
        }

        entity.getPersistentDataContainer().remove(KEY);
    }

    public static boolean isExcluded(Entity entity) {
        if (entity == null) {
            return false;
        }

        PersistentDataContainer container = entity.getPersistentDataContainer();
        return container.has(KEY, PersistentDataType.BYTE);
    }
}
