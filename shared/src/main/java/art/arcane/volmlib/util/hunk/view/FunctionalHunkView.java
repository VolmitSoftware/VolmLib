package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;

import java.util.function.Function;

public class FunctionalHunkView<R, T> implements HunkLike<T> {
    private final HunkLike<R> src;
    private final Function<R, T> converter;
    private final Function<T, R> backConverter;

    public FunctionalHunkView(HunkLike<R> src, Function<R, T> converter, Function<T, R> backConverter) {
        this.src = src;
        this.converter = converter;
        this.backConverter = backConverter;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        if (backConverter == null) {
            throw new UnsupportedOperationException("You cannot writeNodeData to this hunk (Read Only)");
        }

        src.setRaw(x, y, z, backConverter.apply(t));
    }

    @Override
    public T getRaw(int x, int y, int z) {
        if (converter == null) {
            throw new UnsupportedOperationException("You cannot read this hunk (Write Only)");
        }

        return converter.apply(src.getRaw(x, y, z));
    }

    @Override
    public int getWidth() {
        return src.getWidth();
    }

    @Override
    public int getDepth() {
        return src.getDepth();
    }

    @Override
    public int getHeight() {
        return src.getHeight();
    }

    protected HunkLike<R> source() {
        return src;
    }
}
