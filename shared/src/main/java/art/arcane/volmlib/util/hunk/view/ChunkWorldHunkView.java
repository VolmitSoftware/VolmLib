package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Function3;
import art.arcane.volmlib.util.hunk.HunkLike;
import org.bukkit.Chunk;

public class ChunkWorldHunkView<T> implements HunkLike<T> {
    private final Chunk chunk;
    private final int height;
    private final Consumer4<Integer, Integer, Integer, T> setter;
    private final Function3<Integer, Integer, Integer, T> getter;

    public ChunkWorldHunkView(Chunk chunk, int height, Consumer4<Integer, Integer, Integer, T> setter, Function3<Integer, Integer, Integer, T> getter) {
        this.chunk = chunk;
        this.height = height;
        this.setter = setter;
        this.getter = getter;
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
        return height;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        if (t == null) {
            return;
        }

        setter.accept(worldX(x), y, worldZ(z), t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return getter.apply(worldX(x), y, worldZ(z));
    }

    protected Chunk chunk() {
        return chunk;
    }

    protected int worldX(int x) {
        return x + (chunk.getX() * 16);
    }

    protected int worldZ(int z) {
        return z + (chunk.getZ() * 16);
    }
}
