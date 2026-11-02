package art.arcane.volmlib.nativelib.terrain.structure;

public record StructureWaterColumn(boolean wet, boolean river, int floor, int surface) {
    public static final StructureWaterColumn DRY = new StructureWaterColumn(false, false, Integer.MIN_VALUE, Integer.MIN_VALUE);

    public StructureWaterColumn {
        if (river && !wet) {
            throw new IllegalStateException("A river water column must hold water");
        }
    }
}
