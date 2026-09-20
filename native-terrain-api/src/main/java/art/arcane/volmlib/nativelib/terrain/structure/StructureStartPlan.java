package art.arcane.volmlib.nativelib.terrain.structure;

public interface StructureStartPlan {
    String structureKey();
    JigsawSettings jigsaw();
    boolean underground();
    boolean replacesSource();
    StructureTerrainSettings terrain();
    int chunkX();
    int chunkZ();
    int baseY();
    long placementIdentity();
}
