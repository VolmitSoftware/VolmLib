package art.arcane.volmlib.nativelib.terrain.structure;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;

public interface StructureReferencePolicy<P extends StructureStartPlan,
        O extends StructureOwnershipRecordView<O>> extends StructureRecoveryPolicy<P, O> {
    NativeGenerationScope openOriginScope(int chunkX, int chunkZ);
    StructurePlacementDecision resolve(String structureKey, boolean underground);
    void invalidated(String structureKey);
}
