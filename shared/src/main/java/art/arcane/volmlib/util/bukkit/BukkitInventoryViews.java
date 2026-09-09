package art.arcane.volmlib.util.bukkit;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class BukkitInventoryViews {
    private static final Method TOP = method("getTopInventory");
    private static final Method PLAYER = method("getPlayer");

    private BukkitInventoryViews() {
    }

    public static Inventory top(InventoryView view) {
        return (Inventory) invoke(TOP, view);
    }

    public static HumanEntity player(InventoryView view) {
        return (HumanEntity) invoke(PLAYER, view);
    }

    private static Method method(String name) {
        try {
            return InventoryView.class.getMethod(name);
        } catch (NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Object invoke(Method method, InventoryView view) {
        try {
            return method.invoke(view);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Cannot invoke InventoryView." + method.getName(), exception);
        }
    }
}
