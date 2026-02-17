package art.arcane.volmlib.util.mantle.runtime;

import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.DataOutputStream;
import java.io.IOException;

public class TectonicPlate<M> extends art.arcane.volmlib.util.mantle.TectonicPlate<MantleChunk<M>> {
    public static final int MISSING = art.arcane.volmlib.util.mantle.TectonicPlate.MISSING;
    public static final int CURRENT = art.arcane.volmlib.util.mantle.TectonicPlate.CURRENT;

    private static final ThreadLocal<ConstructionContext<?>> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    private MantleDataAdapter<M> adapter;
    private MantleHooks hooks;

    public TectonicPlate(int worldHeight, int x, int z, MantleDataAdapter<M> adapter, MantleHooks hooks) {
        super(worldHeight, x, z);
        this.adapter = requireAdapter(adapter);
        this.hooks = normalizeHooks(hooks);
    }

    private TectonicPlate(int worldHeight, CountingDataInputStream din, boolean versioned) throws IOException {
        super(worldHeight, din, versioned);
    }

    public static <M> TectonicPlate<M> read(int worldHeight,
                                            CountingDataInputStream din,
                                            boolean versioned,
                                            MantleDataAdapter<M> adapter,
                                            MantleHooks hooks) throws IOException {
        ConstructionContext<M> context = new ConstructionContext<>(requireAdapter(adapter), normalizeHooks(hooks));
        CONSTRUCTION_CONTEXT.set(context);
        try {
            TectonicPlate<M> plate = new TectonicPlate<>(worldHeight, din, versioned);
            plate.adapter = context.adapter;
            plate.hooks = context.hooks;
            return plate;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override
    protected void beforeReadChunk(int index) {
        hooks().onBeforeReadChunk(index);
    }

    @Override
    protected void afterReadChunk(int index) {
        hooks().onAfterReadChunk(index);
    }

    @Override
    protected void onReadChunkFailure(int index, long start, long end, CountingDataInputStream din, Throwable error) {
        hooks().onReadChunkFailure(index, start, end, din, error);
    }

    @Override
    protected MantleChunk<M> readChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        return MantleChunk.read(version, sectionHeight, din, adapter(), hooks());
    }

    @Override
    protected MantleChunk<M> createChunk(int sectionHeight, int x, int z) {
        return new MantleChunk<>(sectionHeight, x, z, adapter(), hooks());
    }

    @Override
    protected boolean isChunkInUse(MantleChunk<M> chunk) {
        return chunk.inUse();
    }

    @Override
    protected void closeChunk(MantleChunk<M> chunk) {
        chunk.close();
    }

    @Override
    protected void writeChunk(MantleChunk<M> chunk, DataOutputStream dos) throws IOException {
        chunk.write(dos);
    }

    public static void addError() {
        art.arcane.volmlib.util.mantle.TectonicPlate.addError();
    }

    public static boolean hasError() {
        return art.arcane.volmlib.util.mantle.TectonicPlate.hasError();
    }

    private MantleDataAdapter<M> adapter() {
        MantleDataAdapter<M> local = adapter;
        if (local != null) {
            return local;
        }

        ConstructionContext<?> context = CONSTRUCTION_CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("TectonicPlate adapter context is unavailable.");
        }

        @SuppressWarnings("unchecked")
        MantleDataAdapter<M> resolved = (MantleDataAdapter<M>) context.adapter;
        return resolved;
    }

    private MantleHooks hooks() {
        MantleHooks local = hooks;
        if (local != null) {
            return local;
        }

        ConstructionContext<?> context = CONSTRUCTION_CONTEXT.get();
        if (context == null) {
            return MantleHooks.NONE;
        }

        return context.hooks;
    }

    private static <M> MantleDataAdapter<M> requireAdapter(MantleDataAdapter<M> adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("MantleDataAdapter must not be null.");
        }

        return adapter;
    }

    private static MantleHooks normalizeHooks(MantleHooks hooks) {
        return hooks == null ? MantleHooks.NONE : hooks;
    }

    private static final class ConstructionContext<M> {
        private final MantleDataAdapter<M> adapter;
        private final MantleHooks hooks;

        private ConstructionContext(MantleDataAdapter<M> adapter, MantleHooks hooks) {
            this.adapter = adapter;
            this.hooks = hooks;
        }
    }
}
