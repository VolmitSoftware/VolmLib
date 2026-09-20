package art.arcane.volmlib.nativelib.terrain.structure;

public interface StructureOwnershipRecordView<O extends StructureOwnershipRecordView<O>>
        extends StructureOwnershipView {
    int MAX_REFERENCE_DISTANCE_CHUNKS = 8;

    long placementIdentity();
    int referenceMinChunkX();
    int referenceMaxChunkX();
    int referenceMinChunkZ();
    int referenceMaxChunkZ();
    StructureTerrainSettings terrain();
    O withReferenceEnvelope(StructureReferenceBounds bounds);
    default boolean covers(int chunkX, int chunkZ) {
        return chunkX >= referenceMinChunkX() && chunkX <= referenceMaxChunkX()
                && chunkZ >= referenceMinChunkZ() && chunkZ <= referenceMaxChunkZ();
    }

}
