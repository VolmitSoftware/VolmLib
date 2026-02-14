package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.storage.AtomicHunk;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

@SuppressWarnings("ClassCanBeRecord")
public class BiomeGridHunkHolder extends AtomicHunk<Biome> {
    private final BiomeGrid chunk;
    private final int minHeight;
    private final int maxHeight;

    public BiomeGridHunkHolder(BiomeGrid chunk, int minHeight, int maxHeight) {
        super(16, maxHeight - minHeight, 16);
        this.chunk = chunk;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getDepth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return maxHeight - minHeight;
    }

    public void apply() {
        for (int i = 0; i < getHeight(); i++) {
            for (int j = 0; j < getWidth(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    Biome b = super.getRaw(j, i, k);

                    if (b != null) {
                        chunk.setBiome(j, i + minHeight, k, b);
                    }
                }
            }
        }
    }

    @Override
    public Biome getRaw(int x, int y, int z) {
        Biome b = super.getRaw(x, y, z);

        return b != null ? b : Biome.PLAINS;
    }

    public BiomeGrid getChunk() {
        return chunk;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public int getMaxHeight() {
        return maxHeight;
    }
}
