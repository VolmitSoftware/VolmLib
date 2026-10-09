package art.arcane.volmlib.util.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class BukkitComponents {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final Method GET_NAME = method(ItemMeta.class, "displayName");
    private static final Method SET_NAME = method(ItemMeta.class, "displayName", Component.class);
    private static final Method GET_LORE = method(ItemMeta.class, "lore");
    private static final Method SET_LORE = method(ItemMeta.class, "lore", List.class);
    private static final Method SIZED_INVENTORY = method(Bukkit.class, "createInventory",
            InventoryHolder.class, int.class, Component.class);
    private static final Method TYPED_INVENTORY = method(Bukkit.class, "createInventory",
            InventoryHolder.class, InventoryType.class, Component.class);

    private BukkitComponents() {
    }

    public static Component displayName(ItemMeta meta) {
        if (GET_NAME != null) {
            return (Component) invoke(GET_NAME, meta);
        }
        return meta.hasDisplayName() ? LEGACY.deserialize(meta.getDisplayName()) : null;
    }

    public static void displayName(ItemMeta meta, Component name) {
        if (SET_NAME != null) {
            invoke(SET_NAME, meta, name);
            return;
        }
        meta.setDisplayName(name == null ? null : legacyItem(name));
    }

    @SuppressWarnings("unchecked")
    public static List<Component> lore(ItemMeta meta) {
        if (GET_LORE != null) {
            return (List<Component>) invoke(GET_LORE, meta);
        }
        List<String> lore = meta.getLore();
        if (lore == null) {
            return null;
        }
        List<Component> components = new ArrayList<>(lore.size());
        for (String line : lore) {
            components.add(LEGACY.deserialize(line));
        }
        return components;
    }

    public static void lore(ItemMeta meta, List<? extends Component> lore) {
        if (SET_LORE != null) {
            invoke(SET_LORE, meta, lore);
            return;
        }
        if (lore == null) {
            meta.setLore(null);
            return;
        }
        List<String> lines = new ArrayList<>(lore.size());
        for (Component line : lore) {
            lines.add(legacyItem(line));
        }
        meta.setLore(lines);
    }

    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        if (SIZED_INVENTORY != null) {
            return (Inventory) invoke(SIZED_INVENTORY, null, holder, size, title);
        }
        return Bukkit.createInventory(holder, size, LEGACY.serialize(title));
    }

    public static Inventory createInventory(InventoryHolder holder, InventoryType type, Component title) {
        if (TYPED_INVENTORY != null) {
            return (Inventory) invoke(TYPED_INVENTORY, null, holder, type, title);
        }
        return Bukkit.createInventory(holder, type, LEGACY.serialize(title));
    }

    private static String legacyItem(Component component) {
        String serialized = LEGACY.serialize(component);
        return component.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE
                ? "\u00a7r" + serialized : serialized;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException unavailable) {
            return null;
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access server component method " + method, failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Server component method failed: " + method, cause);
        }
    }
}
