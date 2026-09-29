package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import java.util.function.Function;

public final class NativeBiomeResolver {
    private final Function<Climate.Sampler, BiomeResolver> resolvers;

    public NativeBiomeResolver(Function<Climate.Sampler, BiomeResolver> resolvers) {
        this.resolvers = resolvers;
    }

    void fill(LevelChunk chunk, ServerLevel level) {
        chunk.fillBiomesFromNoise(resolvers.apply(level.getChunkSource().randomState()
                .createClimateSampler(SamplerContext.builder().enableCaches().build())));
    }
}
