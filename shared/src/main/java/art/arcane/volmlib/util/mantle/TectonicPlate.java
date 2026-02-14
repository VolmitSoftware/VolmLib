package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.IOException;

/**
 * Shared concrete tectonic plate base on top of shared plate support runtime.
 */
public abstract class TectonicPlate<C extends MantleChunk<?>> extends TectonicPlateSupport<C> {
    public static final int MISSING = TectonicPlateSupport.MISSING;
    public static final int CURRENT = TectonicPlateSupport.CURRENT;

    protected TectonicPlate(int worldHeight, int x, int z) {
        super(worldHeight, x, z);
    }

    protected TectonicPlate(int worldHeight, CountingDataInputStream din, boolean versioned) throws IOException {
        super(worldHeight, din, versioned);
    }

    public static void addError() {
        TectonicPlateSupport.addError();
    }

    public static boolean hasError() {
        return TectonicPlateSupport.hasError();
    }
}
