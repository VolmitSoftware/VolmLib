package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.util.function.Function;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkDataHunkView implements HunkLike<BlockData> {
    private final ChunkData chunk;
    private final BlockData air;
    private final Function<BlockData, BlockData> dataUnwrapper;

    public ChunkDataHunkView(ChunkData chunk, BlockData air, Function<BlockData, BlockData> dataUnwrapper) {
        this.chunk = chunk;
        this.air = air;
        this.dataUnwrapper = dataUnwrapper;
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

    public void setRegion(int x1, int y1, int z1, int x2, int y2, int z2, BlockData t) {
        if (t == null) {
            return;
        }

        chunk.setRegion(x1, y1 + chunk.getMinHeight(), z1, x2, y2 + chunk.getMinHeight(), z2, unwrap(t));
    }

    public BlockData get(int x, int y, int z) {
        return getRaw(x, y, z);
    }

    public void set(int x, int y, int z, BlockData t) {
        setRaw(x, y, z, t);
    }

    @Override
    public void setRaw(int x, int y, int z, BlockData t) {
        if (t == null) {
            return;
        }

        try {
            chunk.setBlock(x, y + chunk.getMinHeight(), z, unwrap(t));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public BlockData getRaw(int x, int y, int z) {
        try {
            return chunk.getBlockData(x, y + chunk.getMinHeight(), z);
        } catch (Throwable ignored) {
        }

        return air;
    }

    protected ChunkData chunk() {
        return chunk;
    }

    protected BlockData unwrap(BlockData data) {
        if (dataUnwrapper == null) {
            return data;
        }

        return dataUnwrapper.apply(data);
    }
}
