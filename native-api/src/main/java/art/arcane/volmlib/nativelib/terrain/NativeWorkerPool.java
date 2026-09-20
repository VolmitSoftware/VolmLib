package art.arcane.volmlib.nativelib.terrain;

public interface NativeWorkerPool {
    int threadCount() throws ReflectiveOperationException;
    void adjustThreadCount(int count) throws ReflectiveOperationException;
}
