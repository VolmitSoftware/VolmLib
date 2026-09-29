package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ForcedStructureChunkGenerator extends ChunkGenerator {
    private final ChunkGenerator delegate;

    public ForcedStructureChunkGenerator(ChunkGenerator delegate, Holder<Biome> sourceBiome) {
        super(new FixedBiomeSource(sourceBiome));
        this.delegate = delegate;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk) {
        delegate.applyCarvers(region, seed, randomState, biomeManager, structureManager, chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager,
                             RandomState randomState, ChunkAccess protoChunk) {
        delegate.buildSurface(level, structureManager, randomState, protoChunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        delegate.spawnOriginalMobs(worldGenRegion);
    }

    @Override
    public int getGenDepth() {
        return delegate.getGenDepth();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                         StructureManager structureManager,
                                                         ChunkAccess centerChunk) {
        return delegate.fillFromNoise(
                blender, randomState, structureManager, centerChunk);
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type,
                             LevelHeightAccessor heightAccessor, RandomState randomState) {
        return delegate.getBaseHeight(x, z, type, heightAccessor, randomState);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor,
                                     RandomState randomState) {
        return delegate.getBaseColumn(x, z, heightAccessor, randomState);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState,
                                   BlockPos feetPos) {
        delegate.addDebugScreenInfo(result, randomState, feetPos);
    }

}
