package art.arcane.volmlib.util.mantle.runtime;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.parallel.HyperLockSupport;
import art.arcane.volmlib.util.parallel.MultiBurstSupport;

import java.io.File;

public class Mantle<M> extends art.arcane.volmlib.util.mantle.Mantle<TectonicPlate<M>, MantleChunk<M>> {
    private MantleDataAdapter<M> adapter;
    private MantleHooks hooks;

    public Mantle(File dataFolder,
                  int worldHeight,
                  HyperLockSupport hyperLock,
                  MultiBurstSupport ioBurst,
                  RegionIO<TectonicPlate<M>> regionIO,
                  MantleDataAdapter<M> adapter,
                  MantleHooks hooks) {
        this(dataFolder, worldHeight, DEFAULT_LOCK_SIZE, hyperLock, ioBurst, regionIO, adapter, hooks);
    }

    public Mantle(File dataFolder,
                  int worldHeight,
                  int lockSize,
                  HyperLockSupport hyperLock,
                  MultiBurstSupport ioBurst,
                  RegionIO<TectonicPlate<M>> regionIO,
                  MantleDataAdapter<M> adapter,
                  MantleHooks hooks) {
        super(dataFolder, worldHeight, lockSize, hyperLock, ioBurst, regionIO);
        this.adapter = requireAdapter(adapter);
        this.hooks = normalizeHooks(hooks);
    }

    @Override
    protected TectonicPlate<M> createRegion(int x, int z) {
        return new TectonicPlate<>(getWorldHeight(), x, z, adapter(), hooks());
    }

    @Override
    protected <T> void setChunkValue(MantleChunk<M> chunk, int x, int y, int z, T value) {
        M section = chunk.getOrCreate(y >> 4);
        Class<?> type = adapter().classifyValue(value);
        adapter().set(section, x & 15, y & 15, z & 15, type, value);
    }

    @Override
    protected <T> void removeChunkValue(MantleChunk<M> chunk, int x, int y, int z, Class<T> type) {
        M section = chunk.getOrCreate(y >> 4);
        adapter().remove(section, x & 15, y & 15, z & 15, type);
    }

    @Override
    protected <T> T getChunkValue(MantleChunk<M> chunk, int x, int y, int z, Class<T> type) {
        M section = chunk.getOrCreate(y >> 4);
        return adapter().get(section, x & 15, y & 15, z & 15, type);
    }

    @Override
    protected <T> void iterateChunkValues(MantleChunk<M> chunk, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        chunk.iterate(type, iterator);
    }

    @Override
    protected void deleteChunkSlice(MantleChunk<M> chunk, Class<?> type) {
        chunk.deleteSlices(type);
    }

    @Override
    protected boolean shouldRetainSlice(Class<?> sliceType) {
        return hooks().shouldRetainSlice(sliceType);
    }

    @Override
    protected String formatDuration(double millis) {
        return hooks().formatDuration(millis);
    }

    @Override
    protected void onDebug(String message) {
        hooks().onDebug(message);
    }

    @Override
    protected void onWarn(String message) {
        hooks().onWarn(message);
    }

    @Override
    protected void onError(Throwable throwable) {
        hooks().onError(throwable);
    }

    private MantleDataAdapter<M> adapter() {
        MantleDataAdapter<M> local = adapter;
        if (local == null) {
            throw new IllegalStateException("MantleDataAdapter is unavailable.");
        }

        return local;
    }

    private MantleHooks hooks() {
        MantleHooks local = hooks;
        return local == null ? MantleHooks.NONE : local;
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
}
