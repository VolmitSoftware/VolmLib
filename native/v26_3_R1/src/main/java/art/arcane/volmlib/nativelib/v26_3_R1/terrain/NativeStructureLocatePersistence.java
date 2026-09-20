package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.StructureLocateProbe;
import art.arcane.volmlib.nativelib.terrain.StructureLocateLimitException;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class NativeStructureLocatePersistence {
    private static final Logger LOG = Logger.getLogger("VolmLib-Native");
    private static final int MAX_STORAGE_PROBES = 512;
    private static final Method PAPER_LEVEL_TYPE_KEY = paperLevelTypeKey();

    private NativeStructureLocatePersistence() {
    }

    public static Probe probe(ServerLevel level, Structure structure, boolean requireUnreferenced) {
        return probe(level, structure, requireUnreferenced, new ProbeBudget(MAX_STORAGE_PROBES));
    }

    public static ProbeBudget probeBudget() {
        return new ProbeBudget(MAX_STORAGE_PROBES);
    }

    public static Probe probe(ServerLevel level, Structure structure, boolean requireUnreferenced,
                              ProbeBudget budget) {
        return new Probe(level, structure, requireUnreferenced, budget);
    }

    public static final class Probe implements StructureLocateProbe<StructureStart> {
        private final ServerLevel level;
        private final Structure structure;
        private final String structureKey;
        private final boolean requireUnreferenced;
        private final ProbeBudget budget;
        private final Map<Long, Boolean> storedDecisions = new HashMap<>();

        Probe(ServerLevel level, Structure structure, boolean requireUnreferenced,
              ProbeBudget budget) {
            this.level = level;
            this.structure = structure;
            Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Identifier identifier = registry.getKey(structure);
            if (identifier == null) {
                throw new IllegalStateException("Cannot probe an unregistered native structure");
            }
            this.structureKey = identifier.toString();
            this.requireUnreferenced = requireUnreferenced;
            this.budget = budget;
        }

        public boolean accepts(int chunkX, int chunkZ) {
            ChunkAccess loaded = level.getChunkSource().getChunk(
                    chunkX, chunkZ, ChunkStatus.STRUCTURE_STARTS, false);
            if (loaded != null) {
                StructureStart start = loaded.getStartForStructure(structure);
                return start != null && start.isValid()
                        && (!requireUnreferenced || start.canBeReferenced());
            }

            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
            Boolean storedDecision = storedDecisions.get(chunkPos.pack());
            if (storedDecision != null) {
                return storedDecision;
            }
            boolean accepted = budget.acceptsStored(
                    level, chunkPos, structureKey, requireUnreferenced);
            storedDecisions.put(chunkPos.pack(), accepted);
            return accepted;
        }

        public StructureStart verifySelected(int chunkX, int chunkZ) {
            ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = chunk.getStartForStructure(structure);
            if (start == null || !start.isValid()) {
                return null;
            }
            if (requireUnreferenced) {
                if (!start.canBeReferenced()) {
                    return null;
                }
            }
            return start;
        }

        public void reference(StructureStart start) {
            if (requireUnreferenced) {
                level.structureManager().addReference(start);
            }
        }
    }

    public static final class ProbeBudget {
        private final int maximum;
        private final Map<Long, StoredChunkState> storedChunks = new HashMap<>();
        private final ChunkStorageScanner scanner;
        private final StoredChunkDatafixer datafixer;
        private int used;
        private boolean scanFailureReported;

        private ProbeBudget(int maximum) {
            this(maximum, null, ProbeBudget::datafix);
        }

        public ProbeBudget(int maximum, ChunkStorageScanner scanner) {
            this(maximum, scanner, ProbeBudget::datafix);
        }

        public ProbeBudget(int maximum, ChunkStorageScanner scanner,
                    StoredChunkDatafixer datafixer) {
            this.maximum = maximum;
            this.scanner = scanner;
            this.datafixer = Objects.requireNonNull(
                    datafixer, "Stored chunk datafixer must not be null");
        }

        private void claim() {
            if (used >= maximum) {
                throw new StructureLocateLimitException();
            }
            used++;
        }

        private StoredChunkState storedState(ServerLevel level, ChunkPos chunkPos) {
            StoredChunkState cached = storedChunks.get(chunkPos.pack());
            if (cached != null) {
                return cached;
            }
            claim();
            CollectFields fields = new CollectFields(
                    new FieldSelector(IntTag.TYPE, "DataVersion"),
                    new FieldSelector("Level", "Structures", CompoundTag.TYPE, "Starts"),
                    new FieldSelector("structures", CompoundTag.TYPE, "starts"));
            ChunkStorageScanner activeScanner = scanner == null
                    ? level.getChunkSource().chunkMap.chunkScanner()::scanChunk : scanner;
            try {
                activeScanner.scan(chunkPos, fields).join();
            } catch (RuntimeException error) {
                if (!scanFailureReported) {
                    scanFailureReported = true;
                    LOG.log(Level.SEVERE,
                            "Native structure locate could not scan stored chunk state; candidates will be verified by loading their structure starts.",
                            error);
                }
                StoredChunkState unresolved = StoredChunkState.missing();
                storedChunks.put(chunkPos.pack(), unresolved);
                return unresolved;
            }
            Tag result = fields.getResult();
            StoredChunkState resolved;
            try {
                resolved = result instanceof CompoundTag storedChunk
                        ? StoredChunkState.parse(datafixer.datafix(level, storedChunk))
                        : StoredChunkState.missing();
            } catch (RuntimeException error) {
                if (!scanFailureReported) {
                    scanFailureReported = true;
                    LOG.log(Level.SEVERE,
                            "Native structure locate could not datafix stored chunk state; candidates will be verified by loading their structure starts.",
                            error);
                }
                resolved = StoredChunkState.missing();
            }
            storedChunks.put(chunkPos.pack(), resolved);
            return resolved;
        }

        private static CompoundTag datafix(ServerLevel level, CompoundTag storedChunk) {
            int dataVersion = NbtUtils.getDataVersion(storedChunk);
            int currentDataVersion = SharedConstants.getCurrentVersion().dataVersion().version();
            if (dataVersion >= currentDataVersion) {
                return storedChunk;
            }
            if (level == null) {
                throw new IllegalStateException("Stored chunk datafix requires an active level");
            }
            CompoundTag context = chunkDataFixContext(level);
            SimpleRegionStorage.injectDatafixingContext(storedChunk, context);
            return DataFixTypes.CHUNK.updateToCurrentVersion(
                    level.getServer().getFixerUpper(), storedChunk, dataVersion);
        }

        private static CompoundTag chunkDataFixContext(ServerLevel level) {
            CompoundTag context = new CompoundTag();
            String levelIdentifier = level.dimension().identifier().toString();
            if (PAPER_LEVEL_TYPE_KEY == null) {
                context.putString("dimension", levelIdentifier);
            } else {
                context.putString("dimension", paperLevelTypeKey(level).identifier().toString());
                context.putString("level_identifier", levelIdentifier);
            }
            level.getChunkSource().getGenerator().getTypeNameForDataFixer()
                    .ifPresent(identifier -> context.putString("generator", identifier.toString()));
            return context;
        }

        public boolean acceptsStored(ServerLevel level, ChunkPos chunkPos,
                              String structureKey, boolean requireUnreferenced) {
            return storedState(level, chunkPos).accepts(structureKey, requireUnreferenced);
        }

        public int used() {
            return used;
        }
    }

    private static Method paperLevelTypeKey() {
        try {
            return ServerLevel.class.getMethod("getTypeKey");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static ResourceKey<?> paperLevelTypeKey(ServerLevel level) {
        try {
            Object value = PAPER_LEVEL_TYPE_KEY.invoke(level);
            if (value instanceof ResourceKey<?> key) {
                return key;
            }
            throw new IllegalStateException("Paper level type key has an unexpected value");
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new IllegalStateException("Cannot read Paper level type key", error);
        }
    }

    @FunctionalInterface
    public interface ChunkStorageScanner {
        CompletableFuture<Void> scan(ChunkPos chunkPos, StreamTagVisitor visitor);
    }

    @FunctionalInterface
    public interface StoredChunkDatafixer {
        CompoundTag datafix(ServerLevel level, CompoundTag storedChunk);
    }

    private record StoredChunkState(boolean stored, boolean startsPresent, CompoundTag starts) {
        private static StoredChunkState missing() {
            return new StoredChunkState(false, false, new CompoundTag());
        }

        private static StoredChunkState parse(CompoundTag chunk) {
            CompoundTag structures = chunk.getCompoundOrEmpty("structures");
            return structures.getCompound("starts")
                    .map(starts -> new StoredChunkState(true, true, starts))
                    .orElseGet(() -> new StoredChunkState(true, false, new CompoundTag()));
        }

        private boolean accepts(String structureKey, boolean requireUnreferenced) {
            if (!stored || !startsPresent) {
                return true;
            }
            return starts.getCompound(structureKey).map(start -> {
                String id = start.getStringOr("id", "");
                return !StructureStart.INVALID_START_ID.equals(id)
                        && (!requireUnreferenced || start.getIntOr("references", 0) == 0);
            }).orElse(false);
        }
    }
}
