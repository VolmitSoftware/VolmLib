package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.function.Predicate;

final class NativeWorldgenVersion {
    static final ChunkStatus TERRAIN_STATUS = ChunkStatus.TERRAIN;
    static final Class<? extends StructurePlacement> FREQUENCY_PLACEMENT = AbstractSpreadingStructurePlacement.class;

    private NativeWorldgenVersion() {
    }

    static StructureTemplateManager templateManager(ServerLevel level) {
        return level.getStructureTemplateManager();
    }

    static StructureStart generate(Structure structure, Holder<Structure> holder, ResourceKey<Level> dimension,
                                   RegistryAccess registryAccess, ChunkGenerator generator, BiomeSource biomeSource,
                                   RandomState randomState, StructureTemplateManager templateManager, long seed,
                                   ChunkPos chunkPos, int references, LevelHeightAccessor heightAccessor,
                                   Predicate<Holder<Biome>> validBiome) {
        return structure.generate(holder, dimension, registryAccess, generator, biomeSource,
                randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED), randomState,
                templateManager, seed, chunkPos, references, heightAccessor, validBiome);
    }

    static StructureStart startForStructure(StructureManager manager, Structure structure, ChunkAccess chunk) {
        return manager.getStartForStructure(structure, chunk);
    }

    static void setStartForStructure(StructureManager manager, Structure structure, StructureStart start,
                                     ChunkAccess chunk) {
        manager.setStartForStructure(structure, start, chunk);
    }

    static void addReferenceForStructure(StructureManager manager, Structure structure, long reference,
                                         ChunkAccess chunk) {
        manager.addReferenceForStructure(structure, reference, chunk);
    }
}
