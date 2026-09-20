package art.arcane.volmlib.nativelib.terrain.structure;

import java.util.List;

public interface StructureInjectionPolicy<P extends StructureStartPlan> {
    List<P> plansAt(int chunkX, int chunkZ);
    boolean sourceReplaced(String structureKey, boolean underground);
    int surfaceHeight(int blockX, int blockZ);
    void record(P plan, StructureFingerprint fingerprint);
    void discard(String structureKey, int chunkX, int chunkZ);
    void duplicate(String structureKey);
}
