package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.storage.AtomicHunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkDataHunkHolder extends AtomicHunk<BlockData> {
    private final BlockData air;
    private final ChunkData chunk;

    public ChunkDataHunkHolder(ChunkData chunk, BlockData air) {
        super(16, chunk.getMaxHeight() - chunk.getMinHeight(), 16);
        this.chunk = chunk;
        this.air = air;
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
        return chunk.getMaxHeight() - chunk.getMinHeight();
    }

    @Override
    public BlockData getRaw(int x, int y, int z) {
        BlockData b = super.getRaw(x, y, z);

        return b != null ? b : air;
    }

    public void apply() {
        for (int i = 0; i < getHeight(); i++) {
            for (int j = 0; j < getWidth(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    BlockData b = super.getRaw(j, i, k);

                    if (b != null) {
                        chunk.setBlock(j, i + chunk.getMinHeight(), k, b);
                    }
                }
            }
        }
    }

    public ChunkData getChunk() {
        return chunk;
    }
}
