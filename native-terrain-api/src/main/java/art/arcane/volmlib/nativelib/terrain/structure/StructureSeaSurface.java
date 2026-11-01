package art.arcane.volmlib.nativelib.terrain.structure;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

public record StructureSeaSurface(int topY, NativeBlockState fluid) {
    public static final StructureSeaSurface NONE = new StructureSeaSurface(Integer.MIN_VALUE, null);

    public StructureSeaSurface {
        if (fluid == null && topY != Integer.MIN_VALUE) {
            throw new IllegalStateException("Structure sea surface at Y " + topY + " requires a fluid block");
        }
    }
}
