package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBlockColumn;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainColumnPolicy;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainColumnPolicy.ColumnSession;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainColumnPolicy.Query;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainColumnPolicy.FlatColumn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.OptionalInt;

public final class NativeTerrainColumns {
    private final NativeTerrainColumnPolicy policy;
    private final NativeTerrainHeightCache heights = new NativeTerrainHeightCache();

    public NativeTerrainColumns(NativeTerrainColumnPolicy policy) {
        this.policy = policy;
    }

    public void evictRuntime(int runtimeId) {
        heights.evictRuntime(runtimeId);
    }

    public int baseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height) {
        if (policy.usesFlatTerrain()) {
            return policy.flatBaseHeight();
        }
        try (ColumnSession session = policy.openQuery(new Query(x, z, "bukkit_nms_base_height"))) {
            NativeTerrainHeightCache.Query cacheQuery = new NativeTerrainHeightCache.Query(
                    session.runtimeId(), x, z, type, height.getMinY(), height.getHeight());
            OptionalInt resolved = heights.resolvedHeight(cacheQuery, () -> resolvedHeight(session, type, height));
            if (resolved.isPresent()) {
                return resolved.getAsInt();
            }
            boolean ignoreFluid = !type.isOpaque().test(Blocks.WATER.defaultBlockState());
            return height.getMinY() + session.height(ignoreFluid) + 1;
        }
    }

    public NoiseColumn baseColumn(int x, int z, LevelHeightAccessor height) {
        if (policy.usesFlatTerrain()) {
            FlatColumn flat = policy.flatColumn(x, z);
            BlockState[] column = new BlockState[height.getHeight()];
            int floorIndex = flat.floorY() - height.getMinY();
            BlockState floor = BuiltInRegistries.BLOCK.getValue(Identifier.parse(flat.floorBlock())).defaultBlockState();
            for (int index = 0; index < column.length; index++) {
                column[index] = index == floorIndex ? floor : Blocks.AIR.defaultBlockState();
            }
            return new NoiseColumn(height.getMinY(), column);
        }
        try (ColumnSession session = policy.openQuery(new Query(x, z, "bukkit_nms_base_column"))) {
            Optional<? extends NativeBlockColumn> resolved = session.resolvedColumn();
            if (resolved.isPresent()) {
                return NativeTransitionColumn.column(resolved.get(), height, policy::placementKey);
            }
            int block = session.height(true);
            int water = session.height(false);
            BlockState[] column = new BlockState[height.getHeight()];
            for (int index = 0; index < column.length; index++) {
                column[index] = index <= block ? Blocks.STONE.defaultBlockState()
                        : index <= water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
            return new NoiseColumn(height.getMinY(), column);
        }
    }

    private OptionalInt resolvedHeight(ColumnSession session, Heightmap.Types type, LevelHeightAccessor height) {
        Optional<? extends NativeBlockColumn> resolved = session.resolvedColumn();
        return resolved.isPresent()
                ? OptionalInt.of(NativeTransitionColumn.height(resolved.get(), type, height, policy::placementKey))
                : OptionalInt.empty();
    }
}
