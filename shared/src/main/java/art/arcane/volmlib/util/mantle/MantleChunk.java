package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.IOException;

/**
 * Shared concrete mantle chunk base on top of shared chunk support runtime.
 */
public abstract class MantleChunk<M> extends MantleChunkSupport<M> {
    @ChunkCoordinates
    protected MantleChunk(int sectionHeight, int x, int z) {
        super(sectionHeight, x, z);
    }

    protected MantleChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        super(version, sectionHeight, din);
    }

    @Override
    public MantleChunk<M> use() {
        super.use();
        return this;
    }

    public void copyFrom(MantleChunk<M> chunk) {
        super.copyFrom(chunk);
    }

    public void trimSlices() {
        trimSections();
    }
}
