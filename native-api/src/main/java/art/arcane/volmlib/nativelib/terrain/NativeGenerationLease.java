package art.arcane.volmlib.nativelib.terrain;

public interface NativeGenerationLease extends NativeGenerationScope {
    long sessionId();
    void detachThread();
}
