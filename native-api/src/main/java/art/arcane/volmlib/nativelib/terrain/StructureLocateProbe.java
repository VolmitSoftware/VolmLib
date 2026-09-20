package art.arcane.volmlib.nativelib.terrain;

public interface StructureLocateProbe<S> {
    boolean accepts(int chunkX, int chunkZ);
    S verifySelected(int chunkX, int chunkZ);
    void reference(S start);
}
