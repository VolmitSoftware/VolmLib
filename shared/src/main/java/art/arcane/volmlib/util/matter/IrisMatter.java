package art.arcane.volmlib.util.matter;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import art.arcane.volmlib.util.matter.slices.BlockMatter;
import art.arcane.volmlib.util.matter.slices.BooleanMatter;
import art.arcane.volmlib.util.matter.slices.CavernMatter;
import art.arcane.volmlib.util.matter.slices.CompoundMatter;
import art.arcane.volmlib.util.matter.slices.IntMatter;
import art.arcane.volmlib.util.matter.slices.JigsawPieceMatter;
import art.arcane.volmlib.util.matter.slices.JigsawStructureMatter;
import art.arcane.volmlib.util.matter.slices.JigsawStructuresMatter;
import art.arcane.volmlib.util.matter.slices.LongMatter;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import art.arcane.volmlib.util.matter.slices.StringMatter;
import art.arcane.volmlib.util.matter.slices.StructurePOIMatter;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class IrisMatter implements Matter {
    private static final boolean BUKKIT_PRESENT = detectBukkit();
    private static volatile SliceRegistry sliceRegistry = buildRegistry();
    private static final ConcurrentMap<Class<?>, Constructor<?>> SLICE_CONSTRUCTORS = new ConcurrentHashMap<>();

    private static boolean detectBukkit() {
        try {
            Class.forName("org.bukkit.block.data.BlockData", false, IrisMatter.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

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

    private static SliceRegistry buildRegistry() {
        Map<Class<?>, SliceRegistration> byType = new HashMap<>();
        Map<String, SliceRegistration> byId = new HashMap<>();
        register(byType, byId, new BiomeInjectMatter());
        if (BUKKIT_PRESENT) {
            register(byType, byId, new BlockMatter());
        }
        register(byType, byId, new BooleanMatter());
        register(byType, byId, new CavernMatter());
        register(byType, byId, new CompoundMatter());
        register(byType, byId, new IntMatter());
        register(byType, byId, new JigsawPieceMatter());
        register(byType, byId, new JigsawStructureMatter());
        register(byType, byId, new JigsawStructuresMatter());
        register(byType, byId, new LongMatter());
        register(byType, byId, new MarkerMatter());
        register(byType, byId, new StringMatter());
        register(byType, byId, new StructurePOIMatter());
        register(byType, byId, new UpdateMatter());
        return new SliceRegistry(Map.copyOf(byType), Map.copyOf(byId));
    }

    public static void registerSliceType(MatterSlice<?> slice) {
        Objects.requireNonNull(slice, "slice");
        Class<?> type = Objects.requireNonNull(slice.getType(), "slice type");
        registerSliceType(type.getCanonicalName(), slice);
    }

    public static synchronized void registerSliceType(String id, MatterSlice<?> slice) {
        Objects.requireNonNull(slice, "slice");
        SliceRegistration registration = registration(id, slice);
        SliceRegistry current = sliceRegistry;
        SliceRegistration existingType = current.byType().get(registration.type());
        SliceRegistration existingId = current.byId().get(id);
        if (registration.equals(existingType) && registration.equals(existingId)) {
            return;
        }

        if (existingType != null || existingId != null) {
            throw new IllegalArgumentException("Matter slice ID or type is already registered: " + id);
        }

        for (SliceRegistration existing : current.byType().values()) {
            if (existing.codec() == registration.codec()) {
                throw new IllegalArgumentException("Matter slice codec is already registered: " + registration.codec().getName());
            }
        }

        Map<Class<?>, SliceRegistration> byType = new HashMap<>(current.byType());
        Map<String, SliceRegistration> byId = new HashMap<>(current.byId());
        byType.put(registration.type(), registration);
        byId.put(id, registration);
        sliceRegistry = new SliceRegistry(Map.copyOf(byType), Map.copyOf(byId));
    }

    public static String getSliceId(Class<?> type) {
        SliceRegistration registration = sliceRegistry.byType().get(Objects.requireNonNull(type, "slice type"));
        return registration == null ? null : registration.id();
    }

    public static Class<?> getSliceType(String id) {
        SliceRegistration registration = sliceRegistry.byId().get(Objects.requireNonNull(id, "slice ID"));
        return registration == null ? null : registration.type();
    }

    @SuppressWarnings("unchecked")
    private static SliceRegistration registration(String id, MatterSlice<?> slice) {
        Objects.requireNonNull(id, "slice ID");
        Class<?> type = Objects.requireNonNull(slice.getType(), "slice type");
        if (id.isEmpty() || id.length() > 65_535) {
            throw new IllegalArgumentException("Invalid matter slice ID length");
        }

        int utfLength = 0;
        for (int i = 0; i < id.length(); i++) {
            char character = id.charAt(i);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                throw new IllegalArgumentException("Matter slice ID contains whitespace or control characters");
            }
            utfLength += character <= 0x7F ? 1 : character <= 0x7FF ? 2 : 3;
        }
        if (utfLength > 65_535) {
            throw new IllegalArgumentException("Matter slice ID exceeds the UTF header limit");
        }

        return new SliceRegistration(id, type, (Class<? extends MatterSlice<?>>) slice.getClass());
    }

    private static void register(Map<Class<?>, SliceRegistration> byType, Map<String, SliceRegistration> byId, MatterSlice<?> slice) {
        SliceRegistration registration = registration(slice.getType().getCanonicalName(), slice);
        byType.put(registration.type(), registration);
        byId.put(registration.id(), registration);
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
        SliceRegistry current = sliceRegistry;
        SliceRegistration registration = current.byType().get(type);

        if (registration == null && BUKKIT_PRESENT && BlockData.class.isAssignableFrom(type)) {
            registration = current.byType().get(BlockData.class);
        }

        if (registration == null) {
            return null;
        }

        Class<? extends MatterSlice<?>> slicer = registration.codec();
        try {
            return (MatterSlice<T>) constructorFor(slicer).newInstance(getWidth(), getHeight(), getDepth());
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to construct matter slice " + Objects.toString(slicer), e);
        }
    }

    /**
     * Slice creation happens per matter object per slice type, so the reflective lookup is resolved
     * once per slicer class instead of on every call.
     */
    private static Constructor<?> constructorFor(Class<? extends MatterSlice<?>> slicer) {
        Constructor<?> cached = SLICE_CONSTRUCTORS.get(slicer);
        if (cached != null) {
            return cached;
        }

        try {
            Constructor<?> resolved = slicer.getConstructor(int.class, int.class, int.class);
            SLICE_CONSTRUCTORS.putIfAbsent(slicer, resolved);
            return resolved;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Matter slice " + slicer.getCanonicalName() + " has no (int, int, int) constructor", e);
        }
    }

    private record SliceRegistration(String id, Class<?> type, Class<? extends MatterSlice<?>> codec) {
    }

    private record SliceRegistry(Map<Class<?>, SliceRegistration> byType, Map<String, SliceRegistration> byId) {
    }
}
