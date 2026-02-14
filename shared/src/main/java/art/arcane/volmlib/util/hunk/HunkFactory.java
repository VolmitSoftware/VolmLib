package art.arcane.volmlib.util.hunk;

@FunctionalInterface
public interface HunkFactory {
    <T> HunkLike<T> create(int w, int h, int d);
}
