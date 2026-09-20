package art.arcane.volmlib.nativelib.terrain.structure;

import art.arcane.volmlib.util.structure.StructureTerrainMode;
import art.arcane.volmlib.util.structure.StructureCarveShape;

public interface StructureTerrainSettings {
    StructureTerrainSettings SOURCE = new SourceTerrainSettings();

    StructureTerrainMode resolvedMode();
    StructureCarveShape resolvedShape();
    int resolvedFlattenRange();
    int getHorizontalPadding();
    int getCeilingPadding();
    int getFloorPadding();
    StructurePalette getEncasePalette();
    double resolvedErosionStrength();
    double resolvedErosionFrequency();
    double resolvedLobeFrequency();
    double resolvedLobeStrength();
}
