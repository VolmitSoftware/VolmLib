package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class NativeEditWorld {
    private final ServerLevel level;
    private final NativeWorld world;

    public NativeEditWorld(NativeWorld world) {
        this.level = (ServerLevel) world.nativeHandle();
        this.world = world;
    }

    public NativeWorld world() {
        return world;
    }

    public boolean current() {
        MinecraftServer server = level.getServer();
        return server != null && server.getLevel(level.dimension()) == level;
    }

    public boolean represents(NativeWorld other) {
        return other != null && other.nativeHandle() == level;
    }

    public boolean sameWorld(NativeEditWorld other) {
        return other != null && other.level == level;
    }

    public String key() {
        return level.dimension().identifier().toString();
    }

    public int minY() {
        return level.getMinY();
    }

    public int height() {
        return level.getHeight();
    }

    public int maxY() {
        return level.getMaxY();
    }

    public int highest(int x, int z, boolean ignoreFluid) {
        return level.getHeight(ignoreFluid ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }

    public NativeBlockState block(int x, int y, int z) {
        return ModdedBlockState.of(level.getBlockState(new BlockPos(x, y, z)), null);
    }

    public boolean solid(int x, int y, int z) {
        return NativeBlockProperties.isSolid(level.getBlockState(new BlockPos(x, y, z)));
    }

    public void sound(NativeBlockPoint point, String key, SoundOptions options) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(key));
        level.playSound(null, position(point), sound, SoundSource.PLAYERS, options.volume(), options.pitch());
    }

    public BiomeIdentity biome(NativeBlockPoint point) {
        Holder<Biome> holder = level.getBiome(position(point));
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        return new BiomeIdentity(holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null), registry.getId(holder.value()));
    }

    public boolean boxOnlyAir(Bounds bounds) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.min().x(); x <= bounds.max().x(); x++) {
            for (int y = bounds.min().y(); y <= bounds.max().y(); y++) {
                for (int z = bounds.min().z(); z <= bounds.max().z(); z++) {
                    if (!level.getBlockState(cursor.set(x, y, z)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void capture(Bounds bounds, CaptureTarget target) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.min().x(); x <= bounds.max().x(); x++) {
            for (int y = bounds.min().y(); y <= bounds.max().y(); y++) {
                for (int z = bounds.min().z(); z <= bounds.max().z(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.is(Blocks.AIR)) {
                        continue;
                    }
                    int ox = x - bounds.min().x();
                    int oy = y - bounds.min().y();
                    int oz = z - bounds.min().z();
                    target.block(ox, oy, oz, ModdedBlockState.of(state, null));
                    if (state.hasBlockEntity()) {
                        NativeTileData tile = null;
                        try {
                            BlockEntity blockEntity = level.getBlockEntity(cursor);
                            if (blockEntity != null) {
                                String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                                tile = NativeTileData.capture(key, NbtUtils.structureToSnbt(blockEntity.saveWithFullMetadata(level.registryAccess())), target::warning);
                            }
                        } catch (Throwable failure) {
                            target.failure(new BlockFailure(new NativeBlockPoint(x, y, z), failure));
                        }
                        target.tile(ox, oy, oz, tile);
                    }
                }
            }
        }
    }

    public EditSession editSession(EditOptions options) {
        return new EditSession(this, options);
    }

    public boolean restoreTile(NativeBlockPoint point, NativeTileData tile, Consumer<Throwable> failure) {
        BlockPos pos = position(point);
        BlockState state = level.getBlockState(pos);
        BlockState adjusted = tile.adjustBlockState(state);
        if (adjusted != state) {
            level.setBlock(pos, adjusted, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            state = adjusted;
        }
        if (!state.hasBlockEntity()) {
            return false;
        }
        try {
            BlockEntity restored = level.getBlockEntity(pos);
            if (restored == null && state.getBlock() instanceof EntityBlock entityBlock) {
                restored = entityBlock.newBlockEntity(pos, state);
            }
            if (restored == null) {
                return false;
            }
            level.setBlockEntity(restored);
            return tile.apply(restored, level);
        } catch (Throwable error) {
            failure.accept(error);
            return false;
        }
    }

    private static BlockPos position(NativeBlockPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }

    public record EditOptions(int minYInclusive, int maxYExclusive, boolean protectBedrock) {
    }

    public record Bounds(NativeBlockPoint min, NativeBlockPoint max) {
    }

    public record SoundOptions(float volume, float pitch) {
    }

    public record BiomeIdentity(String key, int id) {
    }

    public record BlockFailure(NativeBlockPoint position, Throwable error) {
    }

    public interface CaptureTarget {
        void block(int x, int y, int z, NativeBlockState state);
        void tile(int x, int y, int z, NativeTileData tile);
        void warning(String message);
        void failure(BlockFailure failure);
    }

    public static final class EditSession {
        private final NativeEditWorld world;
        private final Map<BlockPos, BlockState> previous = new HashMap<>();
        private final EditOptions options;

        private EditSession(NativeEditWorld world, EditOptions options) {
            this.world = world;
            this.options = options;
        }

        public WriteResult set(int x, int y, int z, NativeBlockState state) {
            ServerLevel level = world.level;
            if (y < options.minYInclusive() || y >= options.maxYExclusive()) {
                return WriteResult.SKIPPED;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockState current = level.getBlockState(pos);
            if (options.protectBedrock() && current.is(Blocks.BEDROCK)) {
                return WriteResult.SKIPPED;
            }
            BlockState target = (BlockState) state.nativeHandle();
            previous.putIfAbsent(pos, current);
            level.setBlock(pos, target, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            return target.isAir() ? WriteResult.AIR : WriteResult.BLOCK;
        }

        public boolean empty() {
            return previous.isEmpty();
        }

        public NativeEditWorld world() {
            return world;
        }

        public int restore(Consumer<BlockFailure> failures) {
            int writes = 0;
            for (Map.Entry<BlockPos, BlockState> block : previous.entrySet()) {
                try {
                    world.level.setBlock(block.getKey(), block.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    writes++;
                } catch (Throwable failure) {
                    BlockPos pos = block.getKey();
                    failures.accept(new BlockFailure(new NativeBlockPoint(pos.getX(), pos.getY(), pos.getZ()), failure));
                }
            }
            return writes;
        }
    }

    public enum WriteResult {
        SKIPPED, AIR, BLOCK
    }
}
