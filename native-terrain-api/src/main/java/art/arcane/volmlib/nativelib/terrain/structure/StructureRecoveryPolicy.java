package art.arcane.volmlib.nativelib.terrain.structure;

public interface StructureRecoveryPolicy<P extends StructureStartPlan,
        O extends StructureOwnershipRecordView<O>> {
    P matchingPlan(String structureKey, int chunkX, int chunkZ);
    O findPersisted(String structureKey, int chunkX, int chunkZ);
    O capture(StructureFingerprint fingerprint, P plan);
    void record(O ownership);
    void discard(String structureKey, int chunkX, int chunkZ);
    boolean sourceReplaced(String structureKey, boolean underground);
    int surfaceHeight(int blockX, int blockZ);
}
