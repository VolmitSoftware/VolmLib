package art.arcane.volmlib.nativelib.terrain.structure;

@FunctionalInterface
public interface StructureWater {
    StructureWater DRY = (x, z) -> StructureWaterColumn.DRY;

    StructureWaterColumn column(int x, int z);
}
