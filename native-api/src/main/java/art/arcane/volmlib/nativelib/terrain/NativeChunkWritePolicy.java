package art.arcane.volmlib.nativelib.terrain;

public interface NativeChunkWritePolicy {
    boolean allowsNativeChunkWrite(int chunkX, int chunkZ);
}
