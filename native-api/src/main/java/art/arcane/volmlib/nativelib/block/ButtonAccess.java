package art.arcane.volmlib.nativelib.block;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.function.BooleanSupplier;

@NativeBinding("block.NativeButtonAccess")
public interface ButtonAccess {
    Result press(Block block, Player player, BooleanSupplier valid);

    enum Result {
        PRESSED, UNCHANGED, DENIED, INVALIDATED
    }
}
