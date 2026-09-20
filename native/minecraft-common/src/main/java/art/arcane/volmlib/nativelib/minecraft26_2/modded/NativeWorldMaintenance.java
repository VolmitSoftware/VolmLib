package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class NativeWorldMaintenance {
    private static final int SILENT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
    private final ServerLevel level;

    public NativeWorldMaintenance(NativeWorld world) {
        level = (ServerLevel) world.nativeHandle();
    }

    public boolean hasPlayers() {
        return !level.players().isEmpty();
    }

    public boolean hasForcedChunks() {
        return !level.getForceLoadedChunks().isEmpty();
    }

    public void forEachPlayerPosition(Consumer<NativeBlockPoint> action) {
        for (ServerPlayer player : level.players()) {
            BlockPos position = player.blockPosition();
            action.accept(new NativeBlockPoint(position.getX(), position.getY(), position.getZ()));
        }
    }

    public void forEachForcedChunk(LongConsumer action) {
        for (long chunk : level.getForceLoadedChunks()) {
            action.accept(chunk);
        }
    }

    public void addForcedChunks(Set<Long> chunks) {
        chunks.addAll(level.getForceLoadedChunks());
    }

    public static NativeBlockPlacement placement(NativeWorld world, NativeBlockPoint position) {
        return new NativeBlockPlacement((ServerLevel) world.nativeHandle(), position(position));
    }

    public static boolean exposedFluid(NativeWorld world, int x, int y, int z) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        BlockPos position = new BlockPos(x, y, z);
        return NativeBlockProperties.isFluid(level.getBlockState(position))
                && (NativeBlockProperties.isAir(level.getBlockState(position.below()))
                || NativeBlockProperties.isAir(level.getBlockState(position.west()))
                || NativeBlockProperties.isAir(level.getBlockState(position.east()))
                || NativeBlockProperties.isAir(level.getBlockState(position.south()))
                || NativeBlockProperties.isAir(level.getBlockState(position.north())));
    }

    public static void refresh(NativeWorld world, NativeBlockPoint point) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        BlockPos position = position(point);
        BlockState state = level.getBlockState(position);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), SILENT_FLAGS);
        level.setBlock(position, state, Block.UPDATE_ALL);
    }

    public static TileResult applyTile(NativeWorld world, NativeBlockPoint point, NativeTileData tile) throws Exception {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        BlockPos position = position(point);
        BlockState state = level.getBlockState(position);
        BlockState adjusted = tile.adjustBlockState(state);
        if (adjusted != state) {
            level.setBlock(position, adjusted, SILENT_FLAGS);
            state = adjusted;
        }
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return TileResult.INAPPLICABLE;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null) {
            blockEntity = entityBlock.newBlockEntity(position, state);
        }
        if (blockEntity == null) {
            return TileResult.MISSING_ENTITY;
        }
        if (!tile.isApplicable(state, blockEntity)) {
            return TileResult.INAPPLICABLE;
        }
        level.setBlockEntity(blockEntity);
        return tile.apply(blockEntity, level) ? TileResult.APPLIED : TileResult.EMPTY_PAYLOAD;
    }

    private static BlockPos position(NativeBlockPoint position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    public enum TileResult {
        APPLIED, INAPPLICABLE, MISSING_ENTITY, EMPTY_PAYLOAD
    }
}
