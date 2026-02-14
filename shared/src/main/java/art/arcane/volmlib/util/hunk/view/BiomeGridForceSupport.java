package art.arcane.volmlib.util.hunk.view;

import org.bukkit.generator.ChunkGenerator.BiomeGrid;

import java.util.function.Function;

public final class BiomeGridForceSupport {
    private BiomeGridForceSupport() {
    }

    public static void forceBiomeBaseInto(BiomeGrid chunk, int minHeight, int x, int y, int z, Object dirty, Function<BiomeGrid, BiomeGrid> targetResolver, BiomeForceBridge bridge) {
        BiomeGrid target = targetResolver.apply(chunk);
        bridge.forceBiomeInto(x, y + minHeight, z, dirty, target == null ? chunk : target);
    }
}
