package art.arcane.volmlib.nativelib.block;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

@NativeBinding("block.NativeBlockEntityAccess")
public interface BlockEntityAccess {
    byte[] snapshotNbt(BlockState state) throws ReflectiveOperationException;

    boolean matchesLock(BlockState state, ItemStack keyItem) throws ReflectiveOperationException;
}
