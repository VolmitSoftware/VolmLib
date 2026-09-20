package art.arcane.volmlib.nativelib.terrain.structure;

import art.arcane.volmlib.util.structure.StructureTerrainMode;
import art.arcane.volmlib.util.structure.StructureCarveShape;

final class SourceTerrainSettings implements StructureTerrainSettings {
    @Override
    public StructureTerrainMode resolvedMode() {
        return StructureTerrainMode.SOURCE;
    }

    @Override
    public StructureCarveShape resolvedShape() {
        return StructureCarveShape.BOX;
    }

    @Override
    public int resolvedFlattenRange() {
        return 64;
    }

    @Override
    public int getHorizontalPadding() {
        return 0;
    }

    @Override
    public int getCeilingPadding() {
        return 0;
    }

    @Override
    public int getFloorPadding() {
        return 0;
    }

    @Override
    public StructurePalette getEncasePalette() {
        return null;
    }

    @Override
    public double resolvedErosionStrength() {
        return 0.8D;
    }

    @Override
    public double resolvedErosionFrequency() {
        return 0.07D;
    }

    @Override
    public double resolvedLobeFrequency() {
        return 0.07D * 0.3D;
    }

    @Override
    public double resolvedLobeStrength() {
        return 0.85D;
    }
}
