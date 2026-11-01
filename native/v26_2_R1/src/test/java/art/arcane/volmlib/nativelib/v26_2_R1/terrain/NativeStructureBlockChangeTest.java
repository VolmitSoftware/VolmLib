package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureSurfaceFitter;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureTerrainIntegrator;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePalette;
import art.arcane.volmlib.nativelib.terrain.structure.StructureTerrainSettings;
import art.arcane.volmlib.util.structure.StructureCarveShape;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructureBlockChangeTest {
    private static final int ORIGINAL_SURFACE_Y = 67;

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void flattenCutColumnReportsChangedPositionsAndTheTerminatingAir() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int y = 40; y < ORIGINAL_SURFACE_Y; y++) {
            blocks.put(new BlockPos(0, y, 0), Blocks.STONE.defaultBlockState());
        }
        blocks.put(new BlockPos(0, ORIGINAL_SURFACE_Y, 0), Blocks.GRASS_BLOCK.defaultBlockState());
        blocks.put(new BlockPos(0, ORIGINAL_SURFACE_Y + 1, 0), Blocks.DANDELION.defaultBlockState());
        blocks.put(new BlockPos(0, ORIGINAL_SURFACE_Y + 3, 0), Blocks.MOSS_CARPET.defaultBlockState());
        WorldGenLevel delegate = delegate(blocks);
        NativeBlockChangeSet changes = NativeBlockChangeSet.create(
                NativeStructureWorldgenAccess.changeBounds(delegate, center()));
        WorldGenLevel world = NativeStructureWorldgenAccess.create(delegate, center(),
                (x, z) -> ORIGINAL_SURFACE_Y + 1, (x, z) -> ORIGINAL_SURFACE_Y + 1, false, true,
                position -> false, changes);

        NativeStructureSurfaceFitter.prepareSurfaceStructures(world, new BoundingBox(0, 0, 0, 0, 127, 0),
                List.of(new NativeStructureTerrainIntegrator.TerrainTarget(
                        "minecraft:desert_pyramid", desertStart(), new FlattenTerrain(16))),
                (x, z) -> ORIGINAL_SURFACE_Y, NativeStructureSurfaceFitter.SeaSurface.NONE);

        int targetY = desertStart().getBoundingBox().minY() - 1;
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), blocks.get(new BlockPos(0, targetY, 0)));
        assertTrue(blocks.get(new BlockPos(0, ORIGINAL_SURFACE_Y + 1, 0)).isAir());
        assertEquals(Blocks.MOSS_CARPET.defaultBlockState(), blocks.get(new BlockPos(0, ORIGINAL_SURFACE_Y + 3, 0)));
        List<Integer> expected = new ArrayList<>();
        for (int y = targetY; y <= ORIGINAL_SURFACE_Y + 2; y++) {
            expected.add(y);
        }
        assertEquals(expected, reportedHeights(changes));
    }

    @Test
    public void protectedAndRejectedPositionsAreNeverReported() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        WorldGenLevel delegate = delegate(blocks);
        BlockPos protectedPosition = new BlockPos(1, 70, 1);
        NativeBlockChangeSet changes = NativeBlockChangeSet.create(
                NativeStructureWorldgenAccess.changeBounds(delegate, center()));
        NativeStructureWorldgenAccess world = NativeStructureWorldgenAccess.create(delegate, center(),
                (x, z) -> 64, (x, z) -> 64, false, true, protectedPosition::equals, changes);

        assertFalse(world.setBlock(protectedPosition, Blocks.STONE.defaultBlockState(), 2));
        world.recordBlockChange(protectedPosition.getX(), protectedPosition.getY(), protectedPosition.getZ());
        world.recordBlockChange(40, 70, 1);
        assertTrue(world.setBlock(new BlockPos(2, 70, 2), Blocks.STONE.defaultBlockState(), 2));
        world.recordBlockChange(3, 71, 3);

        assertEquals(2, changes.size());
        assertTrue(changes.contains(2, 70, 2));
        assertTrue(changes.contains(3, 71, 3));
        assertFalse(changes.contains(1, 70, 1));
    }

    private static List<Integer> reportedHeights(NativeBlockChangeSet changes) {
        List<Integer> heights = new ArrayList<>();
        changes.forEachChunk((chunkX, chunkZ) -> changes.forEachPosition(chunkX, chunkZ, (x, y, z) -> {
            assertEquals(0, x);
            assertEquals(0, z);
            heights.add(y);
        }));
        return heights;
    }

    private static ChunkPos center() {
        return new ChunkPos(0, 0);
    }

    private static StructureStart desertStart() {
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        DesertPyramidPiece piece = new DesertPyramidPiece(RandomSource.create(7L), 0, 0);
        return new StructureStart(structure, center(), 0, new PiecesContainer(List.of(piece)));
    }

    private static WorldGenLevel delegate(Map<BlockPos, BlockState> blocks) {
        Holder<Biome> biome = Holder.direct((Biome) null);
        BiomeManager biomeManager = new BiomeManager((x, y, z) -> biome, 13L);
        InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
            case "getMinY" -> -64;
            case "getHeight" -> arguments == null ? 384 : ORIGINAL_SURFACE_Y + 1;
            case "getSeaLevel" -> 63;
            case "getBiome" -> biome;
            case "getBiomeManager" -> biomeManager;
            case "getBlockTicks", "getFluidTicks", "getChunk", "getLevel" -> null;
            case "getBlockState" -> blocks.getOrDefault((BlockPos) arguments[0], Blocks.AIR.defaultBlockState());
            case "setBlock" -> {
                blocks.put(((BlockPos) arguments[0]).immutable(), (BlockState) arguments[1]);
                yield true;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            case "toString" -> "native-structure-block-change-world";
            default -> throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private record FlattenTerrain(int flattenRange) implements StructureTerrainSettings {
        @Override
        public StructureTerrainMode resolvedMode() {
            return StructureTerrainMode.FLATTEN;
        }

        @Override
        public StructureCarveShape resolvedShape() {
            return StructureCarveShape.BOX;
        }

        @Override
        public int resolvedFlattenRange() {
            return flattenRange;
        }

        @Override
        public int getHorizontalPadding() {
            return 0;
        }

        @Override
        public int getCeilingPadding() {
            return 0;
        }

        @Override
        public int getFloorPadding() {
            return 0;
        }

        @Override
        public StructurePalette getEncasePalette() {
            return null;
        }

        @Override
        public double resolvedErosionStrength() {
            return 0D;
        }

        @Override
        public double resolvedErosionFrequency() {
            return 0D;
        }

        @Override
        public double resolvedLobeFrequency() {
            return 0D;
        }

        @Override
        public double resolvedLobeStrength() {
            return 0D;
        }
    }
}
