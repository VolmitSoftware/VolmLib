package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.LevelChunk;

public final class NativeBiomeResolver {
    private final BiomeResolver resolver;

    public NativeBiomeResolver(BiomeResolver resolver) {
        this.resolver = resolver;
    }

    void fill(LevelChunk chunk, ServerLevel level) {
        chunk.fillBiomesFromNoise(resolver, level.getChunkSource().randomState().sampler());
    }
}
