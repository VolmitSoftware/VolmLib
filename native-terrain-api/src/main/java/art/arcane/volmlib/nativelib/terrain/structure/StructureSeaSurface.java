package art.arcane.volmlib.nativelib.terrain.structure;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.Objects;

public record StructureSeaSurface(int topY, NativeBlockState fluid) {
    public StructureSeaSurface {
        Objects.requireNonNull(fluid, "Structure sea surface requires a fluid block");
    }
}
