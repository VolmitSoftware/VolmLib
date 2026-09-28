package art.arcane.volmlib.nativelib.terrain;

@FunctionalInterface
public interface NativeBlockVolume {
    NativeBlockState getStoredRaw(int x, int y, int z);

    /**
     * Highest y of the column that may hold a stored state; every cell above it is unwritten.
     */
    default int highestStoredY(int x, int z) {
        return Integer.MAX_VALUE;
    }
}
