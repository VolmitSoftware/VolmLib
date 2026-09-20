package art.arcane.volmlib.nativelib.terrain;

public interface NativeGenerationScope extends AutoCloseable {
    @Override
    void close();
}
