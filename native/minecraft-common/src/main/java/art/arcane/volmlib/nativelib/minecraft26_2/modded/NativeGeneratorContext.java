package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.world.level.biome.BiomeSource;
import java.util.Objects;

public final class NativeGeneratorContext {
    private final BiomeSource biomeSource;
    private final String dimensionKey;

    NativeGeneratorContext(BiomeSource biomeSource, String dimensionKey) {
        this.biomeSource = Objects.requireNonNull(biomeSource);
        this.dimensionKey = Objects.requireNonNull(dimensionKey);
    }

    BiomeSource biomeSource() {
        return biomeSource;
    }

    public String dimensionKey() {
        return dimensionKey;
    }
}
