package art.arcane.volmlib.nativelib.terrain;

@FunctionalInterface
public interface NativeBlockVolume {
    NativeBlockState getStoredRaw(int x, int y, int z);
}
