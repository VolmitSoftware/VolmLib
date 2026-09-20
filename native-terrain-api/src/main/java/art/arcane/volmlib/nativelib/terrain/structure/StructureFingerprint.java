package art.arcane.volmlib.nativelib.terrain.structure;

public record StructureFingerprint(
        String structureKey,
        int originChunkX,
        int originChunkZ,
        int contentMinX,
        int contentMinY,
        int contentMinZ,
        int contentMaxX,
        int contentMaxY,
        int contentMaxZ,
        int locatorY,
        int referenceMinChunkX,
        int referenceMaxChunkX,
        int referenceMinChunkZ,
        int referenceMaxChunkZ,
        String contentFingerprint
) implements StructureOwnershipView {
}
