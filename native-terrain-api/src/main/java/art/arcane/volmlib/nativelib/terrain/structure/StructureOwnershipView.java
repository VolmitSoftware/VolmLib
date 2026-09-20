package art.arcane.volmlib.nativelib.terrain.structure;

public interface StructureOwnershipView {
    String structureKey();
    int originChunkX();
    int originChunkZ();
    int contentMinX();
    int contentMinY();
    int contentMinZ();
    int contentMaxX();
    int contentMaxY();
    int contentMaxZ();
    String contentFingerprint();
}
