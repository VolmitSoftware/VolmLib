package art.arcane.volmlib.util.mantle.runtime;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.DataOutputStream;
import java.io.IOException;

public class MantleChunk<M> extends art.arcane.volmlib.util.mantle.MantleChunk<M> {
    private static final ThreadLocal<ConstructionContext<?>> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    private MantleDataAdapter<M> adapter;
    private MantleHooks hooks;

    public MantleChunk(int sectionHeight, int x, int z, MantleDataAdapter<M> adapter, MantleHooks hooks) {
        super(sectionHeight, x, z);
        this.adapter = requireAdapter(adapter);
        this.hooks = normalizeHooks(hooks);
    }

    private MantleChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        super(version, sectionHeight, din);
    }

    public static <M> MantleChunk<M> read(int version,
                                          int sectionHeight,
                                          CountingDataInputStream din,
                                          MantleDataAdapter<M> adapter,
                                          MantleHooks hooks) throws IOException {
        ConstructionContext<M> context = new ConstructionContext<>(requireAdapter(adapter), normalizeHooks(hooks));
        CONSTRUCTION_CONTEXT.set(context);
        try {
            MantleChunk<M> chunk = new MantleChunk<>(version, sectionHeight, din);
            chunk.adapter = context.adapter;
            chunk.hooks = context.hooks;
            return chunk;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override
    protected void onBeforeReadSection(int index) {
        hooks().onBeforeReadSection(index);
    }

    @Override
    protected void onReadSectionFailure(int index, long start, long end, CountingDataInputStream din, IOException error) {
        hooks().onReadSectionFailure(index, start, end, din, error);
    }

    @Override
    protected M createSection() {
        return adapter().createSection();
    }

    @Override
    protected M readSection(CountingDataInputStream din) throws IOException {
        return adapter().readSection(din);
    }

    @Override
    protected void writeSection(M section, DataOutputStream dos) throws IOException {
        adapter().writeSection(section, dos);
    }

    @Override
    protected void trimSection(M section) {
        adapter().trimSection(section);
    }

    @Override
    protected boolean isSectionEmpty(M section) {
        return adapter().isSectionEmpty(section);
    }

    @Override
    public MantleChunk<M> use() {
        super.use();
        return this;
    }

    public void copyFrom(MantleChunk<M> chunk) {
        super.copyFrom(chunk);
    }

    public <T> T get(int x, int y, int z, Class<T> type) {
        M section = getOrCreate(y >> 4);
        return adapter().get(section, x & 15, y & 15, z & 15, type);
    }

    public <T> void iterate(Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        for (int index = 0; index < sectionCount(); index++) {
            int baseY = index << 4;
            M section = get(index);
            if (section == null) {
                continue;
            }

            adapter().iterate(section, type, (x, y, z, value) -> iterator.accept(x, y + baseY, z, value));
        }
    }

    public void deleteSlices(Class<?> type) {
        for (int index = 0; index < sectionCount(); index++) {
            M section = get(index);
            if (section != null && adapter().hasSlice(section, type)) {
                adapter().deleteSlice(section, type);
            }
        }
    }

    public void trimSlices() {
        trimSections();
    }

    private MantleDataAdapter<M> adapter() {
        MantleDataAdapter<M> local = adapter;
        if (local != null) {
            return local;
        }

        ConstructionContext<?> context = CONSTRUCTION_CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("MantleChunk adapter context is unavailable.");
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
