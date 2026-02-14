package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.hunk.HunkLike;

@SuppressWarnings("ClassCanBeRecord")
public class ListeningHunk<T> implements HunkLike<T> {
    private final HunkLike<T> src;
    private final Consumer4<Integer, Integer, Integer, T> listener;

    public ListeningHunk(HunkLike<T> src, Consumer4<Integer, Integer, Integer, T> listener) {
        this.src = src;
        this.listener = listener;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        listener.accept(x, y, z, t);
        src.setRaw(x, y, z, t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return src.getRaw(x, y, z);
    }

    @Override
    public int getWidth() {
        return src.getWidth();
    }

    @Override
    public int getHeight() {
        return src.getHeight();
    }

    @Override
    public int getDepth() {
        return src.getDepth();
    }

    protected HunkLike<T> source() {
        return src;
    }
}
