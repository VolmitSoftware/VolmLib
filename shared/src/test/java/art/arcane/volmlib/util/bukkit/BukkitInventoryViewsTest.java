package art.arcane.volmlib.util.bukkit;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class BukkitInventoryViewsTest {
    @Test
    public void resolvesAccessThroughRuntimeMethods() {
        Inventory inventory = mock(Inventory.class);
        HumanEntity player = mock(HumanEntity.class);
        InventoryView view = mock(InventoryView.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getTopInventory" -> inventory;
            case "getPlayer" -> player;
            default -> null;
        });
        assertSame(inventory, BukkitInventoryViews.top(view));
        assertSame(player, BukkitInventoryViews.player(view));
    }
}
