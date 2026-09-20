package art.arcane.volmlib.nativelib.terrain.structure;

import java.util.List;

public interface StructureVolumePolicy<C, P extends StructureStartPlan> {
    List<P> plansAt(C context, int chunkX, int chunkZ);
    StructurePlacementDecision decisionFor(C context, P plan);
    StructurePlacementDecision resolve(C context, String structureKey, boolean underground);
    int surfaceHeight(C context, int blockX, int blockZ);
    void warn(String structureKey, Throwable error);
}
