package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashSet;
import java.util.Set;

public final class NativeWorldInspection {
    private final ServerLevel level;

    public NativeWorldInspection(NativeWorld world) {
        level = (ServerLevel) world.nativeHandle();
    }

    public int surfaceY(int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    public BlockEntityDetails blockEntity(NativeBlockPoint position) {
        BlockEntity entity = level.getBlockEntity(new BlockPos(position.x(), position.y(), position.z()));
        if (entity == null) {
            return null;
        }
        String type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString();
        String loot = entity instanceof RandomizableContainer container && container.getLootTable() != null
                ? container.getLootTable().identifier().toString() : null;
        String spawned = null;
        if (entity instanceof SpawnerBlockEntity) {
            CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
            spawned = tag.getCompound("SpawnData").flatMap(data -> data.getCompound("entity"))
                    .flatMap(data -> data.getString("id")).orElse(null);
        }
        return new BlockEntityDetails(type, loot, spawned);
    }

    public static HeldItem heldItem(NativeProtocolPlayer player) {
        ItemStack stack = player.player().getMainHandItem();
        if (stack.isEmpty()) {
            return null;
        }
        String state = stack.getItem() instanceof BlockItem block
                ? ModdedBlockState.serialize(block.getBlock().defaultBlockState()) : null;
        return new HeldItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), state);
    }

    public record BlockEntityDetails(String type, String lootTable, String spawnedEntity) {
    }

    public record HeldItem(String key, int count, String blockState) {
    }

    public String generatorClass() {
        return level.getChunkSource().getGenerator().getClass().getName();
    }

    public NativeBlockPoint spawn() {
        BlockPos spawn = level.getRespawnData().pos();
        return new NativeBlockPoint(spawn.getX(), spawn.getY(), spawn.getZ());
    }

    public Surface surface(int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return new Surface(y, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    public Column column(int x, int z, int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Column sample step must be positive");
        }
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
        int nonEmpty = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir()) {
                nonEmpty++;
            }
        }
        Set<String> blocks = new LinkedHashSet<>();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = level.getMinY(); y < surface; y += step) {
            BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir()) {
                blocks.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            }
        }
        return new Column(nonEmpty, chunk.getSections().length, blocks);
    }

    public Dimension dimension() {
        return dimension(level.dimensionType());
    }

    public Persistence persistence() {
        ItemEntity item = new ItemEntity(level, 0D, level.getMinY(), 0D, Items.COBBLESTONE.getDefaultInstance());
        boolean vanilla = item.shouldBeSaved();
        NativeEntityBehavior.configurePersistence(item, false);
        boolean suppressed = !item.shouldBeSaved();
        NativeEntityBehavior.configurePersistence(item, true);
        return new Persistence(vanilla, suppressed, item.shouldBeSaved());
    }

    public static Dimension dimension(DimensionType dimension) {
        return new Dimension(dimension.minY(), dimension.height(), dimension.logicalHeight(),
                dimension.coordinateScale(), dimension.ambientLight(), dimension.hasSkyLight(),
                dimension.hasCeiling(), dimension.hasEnderDragonFight(), dimension.monsterSpawnBlockLightLimit());
    }

    public record Surface(int y, String block) {
    }

    public record Column(int nonEmptySections, int totalSections, Set<String> blocks) {
    }

    public record Persistence(boolean vanilla, boolean suppressed, boolean restored) {
    }

    public record Dimension(int minY, int height, int logicalHeight, double coordinateScale,
                            float ambientLight, boolean hasSkyLight, boolean hasCeiling,
                            boolean hasEnderDragonFight, int monsterSpawnBlockLightLimit) {
    }
}
