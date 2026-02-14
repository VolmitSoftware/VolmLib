package art.arcane.volmlib.util.hunk;

public interface HunkLike<T> {
    int getWidth();

    int getDepth();

    int getHeight();

    void setRaw(int x, int y, int z, T t);

    T getRaw(int x, int y, int z);
}
