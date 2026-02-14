package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

@SuppressWarnings("ClassCanBeRecord")
public class BiomeGridHunkView implements HunkLike<Biome> {
    private final BiomeGrid chunk;
    private final int minHeight;
    private final int maxHeight;
    private int highest = -1000;

    public BiomeGridHunkView(BiomeGrid chunk, int minHeight, int maxHeight) {
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

    @Override
    public void setRaw(int x, int y, int z, Biome t) {
        chunk.setBiome(x, y + minHeight, z, t);

        if (y > highest) {
            highest = y;
        }
    }

    @Override
    public Biome getRaw(int x, int y, int z) {
        return chunk.getBiome(x, y + minHeight, z);
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

    public int getHighest() {
        return highest;
    }
}
