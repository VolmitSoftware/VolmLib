package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import java.util.Objects;
import java.util.function.Function;

public final class NativeChunkGeneratorDefinition {
    private final String namespace;
    private final String name;
    private final Function<NativeGeneratorContext, ? extends NativeGeneratorOwner> factory;
    private final MapCodec<NativeModdedChunkGenerator<?, ?, ?>> codec;

    public NativeChunkGeneratorDefinition(String namespace, String name,
                                          Function<NativeGeneratorContext, ? extends NativeGeneratorOwner> factory) {
        this.namespace = Objects.requireNonNull(namespace);
        this.name = Objects.requireNonNull(name);
        this.factory = Objects.requireNonNull(factory);
        this.codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BiomeSource.CODEC.fieldOf("biome_source").forGetter((NativeModdedChunkGenerator<?, ?, ?> generator) -> generator.context().biomeSource()),
                Codec.STRING.fieldOf("dimension").forGetter((NativeModdedChunkGenerator<?, ?, ?> generator) -> generator.context().dimensionKey())
        ).apply(instance, this::create));
    }

    private NativeModdedChunkGenerator<?, ?, ?> create(BiomeSource biomes, String key) {
        NativeModdedChunkGenerator<?, ?, ?> generator = (NativeModdedChunkGenerator<?, ?, ?>) factory.apply(new NativeGeneratorContext(biomes, key)).nativeGenerator();
        return generator;
    }

    public String namespace() {
        return namespace;
    }
    public String name() {
        return name;
    }
    public MapCodec<? extends ChunkGenerator> codec() {
        return codec;
    }
}
