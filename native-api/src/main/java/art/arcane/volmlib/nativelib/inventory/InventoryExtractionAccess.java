package art.arcane.volmlib.nativelib.inventory;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.inventory.Inventory;

import java.util.function.IntPredicate;

@NativeBinding("inventory.NativeInventoryExtractionAccess")
public interface InventoryExtractionAccess {
    Inventory filterExtraction(Inventory source, IntPredicate allowedSlot);
}
