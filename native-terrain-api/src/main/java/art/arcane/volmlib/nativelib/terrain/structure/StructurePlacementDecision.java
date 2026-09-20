package art.arcane.volmlib.nativelib.terrain.structure;

public interface StructurePlacementDecision {
    boolean generate();
    boolean preserveSourceY();
    StructureVerticalBand yBand();
    int yShift();
    StructureStiltSettings stilt();
    StructureTerrainSettings terrain();
}
