package art.arcane.volmlib.nativelib.terrain.structure;

public interface StructureLocateCandidate {
    boolean found();
    boolean limitReached();
    int originX();
    int baseY();
    int originZ();
}
