package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStiltSettings;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;
import java.util.function.IntBinaryOperator;

public final class NativeStructurePostProcessor {
    private NativeStructurePostProcessor() {
    }

    public static void place(WorldGenLevel world, StructureManager structureManager, ChunkGenerator generator,
                             WorldgenRandom random, BoundingBox area, ChunkPos chunkPos, String structureId,
                             StructureStart start, StructurePlacementDecision decision,
                             NativeStructureTerrainIntegrator.PaletteBlockResolver paletteBlockResolver,
                             IntBinaryOperator surfaceHeight) {
        StructureStiltSettings stilt = decision.stilt();
        NativeStructureVerticalPlacer.ensureMonumentSeaLevelAlignment(start, structureId, decision.yShift(),
                generator.getSeaLevel(), area.minY(), area.maxY() + 1);
        start.placeInChunk(world, structureManager, generator, random, area, chunkPos);
        if (stilt != null) {
            NativeStructureFoundationBuilder.placeStilts(world, area, structureId, start, stilt,
                    paletteBlockResolver, surfaceHeight,
                    !NativeStructureVegetationClearer.isUndergroundStep(start.getStructure().step()));
        }
    }

    public static void prepareTerrain(WorldGenLevel world, BoundingBox area,
                                      List<NativeStructureTerrainIntegrator.TerrainTarget> targets,
                                      NativeStructureTerrainIntegrator.PaletteBlockResolver paletteBlockResolver) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        NativeStructureTerrainIntegrator.SourceTerrainSnapshot sourceTerrain =
                NativeStructureTerrainIntegrator.captureSourceTerrain(world, area, targets);
        for (NativeStructureTerrainIntegrator.TerrainTarget target : targets) {
            NativeStructureTerrainIntegrator.integrateTerrain(world, area, target.structureId(), target.start(),
                    target.terrain(), paletteBlockResolver, sourceTerrain);
        }
        for (NativeStructureTerrainIntegrator.TerrainTarget target : targets) {
            if (NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                    target.start(), target.terrain())) {
                NativeStructureTerrainIntegrator.clearLegacyTemplateAir(
                        world, area, target.start(), () -> world.getLevel().getStructureManager());
            }
        }
        NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world, area, targets, () -> world.getLevel().getStructureManager());
    }

}
