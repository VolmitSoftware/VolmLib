package art.arcane.volmlib.util.hunk.view;

import org.bukkit.generator.ChunkGenerator.BiomeGrid;

@FunctionalInterface
public interface BiomeForceBridge {
    void forceBiomeInto(int x, int y, int z, Object dirty, BiomeGrid biomeTarget);
}
