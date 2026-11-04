package art.arcane.volmlib.nativelib.terrain.structure;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

@FunctionalInterface
public interface StructureStrata {
    Column column(int x, int z, int surfaceY, boolean submerged);

    interface Column {
        NativeBlockState block(int y);

        int soilDepth();
    }
}
