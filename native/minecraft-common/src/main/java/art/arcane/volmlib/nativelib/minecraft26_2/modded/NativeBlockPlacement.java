package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public final class NativeBlockPlacement {
    private final ServerLevel level;
    private final BlockPos position;
    private final ModdedBlockState blockState;

    public NativeBlockPlacement(ServerLevel level, BlockPos position) {
        this.level = Objects.requireNonNull(level, "level");
        this.position = Objects.requireNonNull(position, "position").immutable();
        this.blockState = ModdedBlockState.of(level.getBlockState(position), null);
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos position() {
        return position;
    }

    public ModdedBlockState blockState() {
        return blockState;
    }
}
