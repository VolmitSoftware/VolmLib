package art.arcane.volmlib.nativelib.item;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;

@NativeBinding("item.ItemStackAccessImpl")
public interface ItemStackAccess {
    byte[] encode(ItemStack item) throws IOException;

    ItemStack decode(byte[] encoded) throws IOException;
}
