package art.arcane.volmlib.nativelib.terrain;

@FunctionalInterface
public interface NativeBlockPositionPredicate {
    boolean test(int x, int y, int z);
}
