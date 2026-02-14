package art.arcane.volmlib.util.matter;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.matter.slices.*;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.Objects;

public class IrisMatter implements Matter {
    private static final KMap<Class<?>, Class<? extends MatterSlice<?>>> SLICERS = buildSlicers();

    private final MatterHeader header;
    private final int width;
    private final int height;
    private final int depth;
    private final KMap<Class<?>, MatterSlice<?>> sliceMap;

    public IrisMatter(int width, int height, int depth) {
        if (width < 1 || height < 1 || depth < 1) {
            throw new IllegalArgumentException("Invalid matter size " + width + "x" + height + "x" + depth);
        }

        this.width = width;
        this.height = height;
        this.depth = depth;
        this.header = new MatterHeader();
        this.sliceMap = new KMap<>();
    }

    private static KMap<Class<?>, Class<? extends MatterSlice<?>>> buildSlicers() {
        KMap<Class<?>, Class<? extends MatterSlice<?>>> slicers = new KMap<>();
        register(slicers, new BiomeInjectMatter());
        register(slicers, new BlockMatter());
        register(slicers, new BooleanMatter());
        register(slicers, new CavernMatter());
        register(slicers, new CompoundMatter());
        register(slicers, new IntMatter());
        register(slicers, new JigsawPieceMatter());
        register(slicers, new JigsawStructureMatter());
        register(slicers, new JigsawStructuresMatter());
        register(slicers, new LongMatter());
        register(slicers, new MarkerMatter());
        register(slicers, new StringMatter());
        register(slicers, new StructurePOIMatter());
        register(slicers, new UpdateMatter());
        return slicers;
    }

    @SuppressWarnings("unchecked")
    private static void register(Map<Class<?>, Class<? extends MatterSlice<?>>> slicers, MatterSlice<?> slice) {
        slicers.put(slice.getType(), (Class<? extends MatterSlice<?>>) slice.getClass());
    }

    @Override
    public MatterHeader getHeader() {
        return header;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public Map<Class<?>, MatterSlice<?>> getSliceMap() {
        return sliceMap;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MatterSlice<T> createSlice(Class<T> type, Matter matter) {
        Class<? extends MatterSlice<?>> slicer = SLICERS.get(type);

        if (slicer == null && BlockData.class.isAssignableFrom(type)) {
            slicer = SLICERS.get(BlockData.class);
        }

        if (slicer == null) {
            return null;
        }

        try {
            return (MatterSlice<T>) slicer.getConstructor(int.class, int.class, int.class)
                    .newInstance(getWidth(), getHeight(), getDepth());
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to construct matter slice " + Objects.toString(slicer), e);
        }
    }
}
