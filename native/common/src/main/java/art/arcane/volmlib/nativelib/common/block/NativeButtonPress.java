package art.arcane.volmlib.nativelib.common.block;

import art.arcane.volmlib.nativelib.block.ButtonAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public class NativeButtonPress implements ButtonAccess {
    @Override
    public Result press(Block block, Player player, BooleanSupplier valid) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(valid, "valid");
        if (!valid.getAsBoolean()) {
            return Result.INVALIDATED;
        }
        BlockData expected = block.getBlockData();
        if (!(expected instanceof Switch button) || button.isPowered()
                || !block.getType().name().endsWith("_BUTTON")) {
            return Result.UNCHANGED;
        }
        BlockRedstoneEvent event = new BlockRedstoneEvent(block, 0, 15);
        player.getServer().getPluginManager().callEvent(event);
        if (!valid.getAsBoolean() || !expected.equals(block.getBlockData())) {
            return Result.INVALIDATED;
        }
        if (event.getNewCurrent() <= 0) {
            return Result.DENIED;
        }
        ServerLevel level = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos position = new BlockPos(block.getX(), block.getY(), block.getZ());
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof ButtonBlock nativeButton)) {
            return Result.INVALIDATED;
        }
        nativeButton.press(state, level, position, ((CraftPlayer) player).getHandle());
        return Result.PRESSED;
    }
}
