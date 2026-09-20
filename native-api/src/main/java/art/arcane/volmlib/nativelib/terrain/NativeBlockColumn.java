package art.arcane.volmlib.nativelib.terrain;

@FunctionalInterface
public interface NativeBlockColumn {
    String stateKeyAt(int worldY);
}
