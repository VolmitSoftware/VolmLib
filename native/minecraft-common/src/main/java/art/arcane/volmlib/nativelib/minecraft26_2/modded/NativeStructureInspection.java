package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import java.util.function.Predicate;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureFoundationBuilder;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVegetationClearer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntBinaryOperator;

public final class NativeStructureInspection {
    private static final int MAX_FOOTPRINT_CHUNKS = 96;
    private static final int MAX_START_REFERENCE_CHUNKS = 16;
    private static final int MAX_STRUCTURE_CANDIDATES = 1024;
    private final ServerLevel level;
    private final Registry<Structure> registry;

    public NativeStructureInspection(NativeWorld world) {
        level = (ServerLevel) world.nativeHandle();
        registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    }

    public List<Reference> resolve(List<String> keys) {
        List<Reference> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            Identifier id = Identifier.tryParse(key);
            if (id == null) {
                continue;
            }
            registry.get(id).ifPresent(holder -> result.add(new Reference(id.toString(), holder)));
        }
        return result;
    }

    public boolean reachable(Reference reference) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return generator instanceof NativeStructureReachability reachable
                && reachable.isNativeStructureReachable(reference.holder);
    }

    public Found find(List<Reference> references, NativeBlockPoint origin, int radius) {
        List<Holder<Structure>> holders = new ArrayList<>(references.size());
        for (Reference reference : references) {
            holders.add(reference.holder);
        }
        Pair<BlockPos, Holder<Structure>> result = findGeneratedStructureCandidate(level, holders,
                new BlockPos(origin.x(), origin.y(), origin.z()), radius);
        if (result == null) {
            return null;
        }
        Identifier key = registry.getKey(result.getSecond().value());
        BlockPos position = result.getFirst();
        return new Found(new NativeBlockPoint(position.getX(), position.getY(), position.getZ()),
                new Reference(key == null ? null : key.toString(), result.getSecond()));
    }

    public Evidence evidence(Found found) {
        NativeBlockPoint position = found.position();
        ChunkAccess chunk = level.getChunk(position.x() >> 4, position.z() >> 4);
        Structure structure = found.structure().holder.value();
        StructureStart start = resolveStructureStart(level, chunk, structure);
        return new Evidence(start == null ? null : new Start(start, structure),
                chunk.getReferencesForStructure(structure).size());
    }

    public FootprintAudit footprint(Start start, FootprintOptions options) {
        return auditFootprint(level, start.structure, start.start, options);
    }

    public PoiAudit pois(Start start) {
        return auditStructurePois(level, start.start);
    }

    public record FootprintOptions(Predicate<String> characteristicMaterial, boolean inspectVegetation,
                                   boolean inspectFoundation, NativeBlockState foundationMaterial,
                                   IntBinaryOperator surfaceHeight) {
    }

    public record Found(NativeBlockPoint position, Reference structure) {
    }

    public record Evidence(Start start, int references) {
        public boolean valid() {
            return start != null && start.start.isValid();
        }
    }

    public static final class Reference {
        private final String key;
        private final Holder<Structure> holder;

        private Reference(String key, Holder<Structure> holder) {
            this.key = key;
            this.holder = holder;
        }

        public String key() {
            return key;
        }
    }

    public static final class Start {
        private final StructureStart start;
        private final Structure structure;

        private Start(StructureStart start, Structure structure) {
            this.start = start;
            this.structure = structure;
        }

        public int chunkX() { return start.getChunkPos().x(); }
        public int chunkZ() { return start.getChunkPos().z(); }
        public int minY() { return start.getBoundingBox().minY(); }
        public int maxY() { return start.getBoundingBox().maxY(); }
        public boolean underground() { return NativeStructureVegetationClearer.isUndergroundStep(structure.step()); }
    }

    private static StructureStart resolveStructureStart(ServerLevel level, ChunkAccess targetChunk,
                                                        Structure structure) {
        StructureStart direct = targetChunk.getStartForStructure(structure);
        if (direct != null && direct.isValid()) {
            return direct;
        }
        int checked = 0;
        for (long packed : targetChunk.getReferencesForStructure(structure)) {
            if (checked++ >= MAX_START_REFERENCE_CHUNKS) {
                break;
            }
            ChunkAccess referencedChunk = level.getChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            StructureStart referenced = referencedChunk.getStartForStructure(structure);
            if (referenced != null && referenced.isValid()) {
                return referenced;
            }
        }
        return direct;
    }

    private static Pair<BlockPos, Holder<Structure>> findGeneratedStructureCandidate(
            ServerLevel level, List<Holder<Structure>> structures, BlockPos origin, int maxRadius) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Set<Long> attempted = new LinkedHashSet<>();
        for (Holder<Structure> structure : structures) {
            for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
                if (!(placement instanceof ConcentricRingsStructurePlacement rings)) {
                    continue;
                }
                List<ChunkPos> positions = state.getRingPositionsFor(rings);
                if (positions == null) {
                    continue;
                }
                List<ChunkPos> sorted = new ArrayList<>(positions);
                sorted.sort(Comparator.comparingLong(position -> distanceSquared(origin, position)));
                for (ChunkPos position : sorted) {
                    Pair<BlockPos, Holder<Structure>> found = inspectStructureCandidate(
                            level, structures, placement, position, attempted);
                    if (found != null) {
                        return found;
                    }
                    if (attempted.size() >= MAX_STRUCTURE_CANDIDATES) {
                        return null;
                    }
                }
            }
        }

        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (Holder<Structure> structure : structures) {
                for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
                    if (!(placement instanceof RandomSpreadStructurePlacement randomSpread)) {
                        continue;
                    }
                    for (int x = -radius; x <= radius; x++) {
                        boolean xEdge = x == -radius || x == radius;
                        for (int z = -radius; z <= radius; z++) {
                            if (!xEdge && z != -radius && z != radius) {
                                continue;
                            }
                            int sectorX = originChunkX + randomSpread.spacing() * x;
                            int sectorZ = originChunkZ + randomSpread.spacing() * z;
                            ChunkPos candidate = randomSpread.getPotentialStructureChunk(
                                    state.getLevelSeed(), sectorX, sectorZ);
                            if (!placement.isStructureChunk(state, candidate.x(), candidate.z())) {
                                continue;
                            }
                            Pair<BlockPos, Holder<Structure>> found = inspectStructureCandidate(
                                    level, structures, placement, candidate, attempted);
                            if (found != null) {
                                return found;
                            }
                            if (attempted.size() >= MAX_STRUCTURE_CANDIDATES) {
                                return null;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Pair<BlockPos, Holder<Structure>> inspectStructureCandidate(
            ServerLevel level, List<Holder<Structure>> structures, StructurePlacement placement,
            ChunkPos candidate, Set<Long> attempted) {
        if (!attempted.add(candidate.pack())) {
            return null;
        }
        ChunkAccess chunk = level.getChunk(candidate.x(), candidate.z());
        for (Holder<Structure> structure : structures) {
            StructureStart start = resolveStructureStart(level, chunk, structure.value());
            if (start == null || !start.isValid()) {
                continue;
            }
            BlockPos locate = placement.getLocatePos(start.getChunkPos());
            BlockPos resolved = new BlockPos(locate.getX(), start.getBoundingBox().minY(), locate.getZ());
            return Pair.of(resolved, structure);
        }
        return null;
    }

    private static long distanceSquared(BlockPos origin, ChunkPos position) {
        long x = (long) position.getMinBlockX() - origin.getX();
        long z = (long) position.getMinBlockZ() - origin.getZ();
        return x * x + z * z;
    }

    private static FootprintAudit auditFootprint(ServerLevel level, Structure structure, StructureStart start,
                                                 FootprintOptions options) {
        List<StructurePiece> pieces = start.getPieces();
        BoundingBox bounds = start.getBoundingBox();
        int availableChunks = footprintChunkCount(bounds);
        List<ChunkPos> selected = selectFootprintChunks(start, MAX_FOOTPRINT_CHUNKS);
        int evidenceChunks = 0;
        int blockEntityStates = 0;
        int blockEntitiesPresent = 0;
        int materialScannedChunks = 0;
        int characteristicBlocks = 0;
        int characteristicChunks = 0;
        int vegetationBlocks = 0;
        int vegetationColumns = 0;
        int foundationBaseColumns = 0;
        int foundationBlocks = 0;
        int foundationColumns = 0;
        int foundationGapColumns = 0;
        BitSet visited = new BitSet(level.getHeight() << 8);
        int[] maximumPieceY = new int[256];
        for (ChunkPos chunkPos : selected) {
            ChunkAccess chunk = level.getChunk(chunkPos.x(), chunkPos.z());
            StructureStart localStart = chunk.getStartForStructure(structure);
            boolean validStart = localStart != null && localStart.isValid();
            int references = chunk.getReferencesForStructure(structure).size();
            if ((validStart || references > 0)) {
                evidenceChunks++;
            }
            BlockEntityAudit blockEntities = auditBlockEntities(level, chunk);
            blockEntityStates += blockEntities.states();
            blockEntitiesPresent += blockEntities.present();
            StructureMaterialAudit material = auditStructureMaterial(level, chunk, start, options, visited, maximumPieceY);
            if (material.scanned()) {
                materialScannedChunks++;
            }
            characteristicBlocks += material.characteristicBlocks();
            if (material.characteristicBlocks() > 0) {
                characteristicChunks++;
            }
            vegetationBlocks += material.vegetationBlocks();
            vegetationColumns += material.vegetationColumns();
            foundationBaseColumns += material.foundationBaseColumns();
            foundationBlocks += material.foundationBlocks();
            foundationColumns += material.foundationColumns();
            foundationGapColumns += material.foundationGapColumns();
        }
        int coveredPieces = 0;
        for (StructurePiece piece : pieces) {
            boolean covered = false;
            for (ChunkPos chunkPos : selected) {
                if (intersectsChunk(piece.getBoundingBox(), chunkPos)) {
                    covered = true;
                    break;
                }
            }
            if (covered) {
                coveredPieces++;
            }
        }
        return new FootprintAudit(selected.size(), availableChunks, evidenceChunks,
                coveredPieces, pieces.size(), blockEntityStates, blockEntitiesPresent,
                materialScannedChunks, characteristicBlocks, characteristicChunks,
                vegetationBlocks, vegetationColumns, foundationBaseColumns, foundationBlocks,
                foundationColumns, foundationGapColumns);
    }

    private static StructureMaterialAudit auditStructureMaterial(ServerLevel level, ChunkAccess chunk,
                                                                  StructureStart start,
                                                                  FootprintOptions options,
                                                                  BitSet visited,
                                                                  int[] maximumPieceY) {
        List<StructurePiece> pieces = start.getPieces();
        visited.clear();
        Arrays.fill(maximumPieceY, Integer.MIN_VALUE);
        int characteristicBlocks = 0;
        int minimumWorldY = level.getMinY();
        int maximumWorldY = level.getMaxY() - 1;
        int minimumChunkX = chunk.getPos().getMinBlockX();
        int maximumChunkX = chunk.getPos().getMaxBlockX();
        int minimumChunkZ = chunk.getPos().getMinBlockZ();
        int maximumChunkZ = chunk.getPos().getMaxBlockZ();
        boolean scanned = false;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (StructurePiece piece : pieces) {
            BoundingBox bounds = piece.getBoundingBox();
            int minimumX = Math.max(minimumChunkX, bounds.minX());
            int maximumX = Math.min(maximumChunkX, bounds.maxX());
            int minimumY = Math.max(minimumWorldY, bounds.minY());
            int maximumY = Math.min(maximumWorldY, bounds.maxY());
            int minimumZ = Math.max(minimumChunkZ, bounds.minZ());
            int maximumZ = Math.min(maximumChunkZ, bounds.maxZ());
            if (minimumX > maximumX || minimumY > maximumY || minimumZ > maximumZ) {
                continue;
            }
            scanned = true;
            for (int z = minimumZ; z <= maximumZ; z++) {
                int localZ = z - minimumChunkZ;
                for (int x = minimumX; x <= maximumX; x++) {
                    int column = (localZ << 4) | (x - minimumChunkX);
                    maximumPieceY[column] = Math.max(maximumPieceY[column], maximumY);
                }
            }
            for (int y = minimumY; y <= maximumY; y++) {
                int verticalIndex = (y - minimumWorldY) << 8;
                for (int z = minimumZ; z <= maximumZ; z++) {
                    int localZ = z - minimumChunkZ;
                    for (int x = minimumX; x <= maximumX; x++) {
                        int column = (localZ << 4) | (x - minimumChunkX);
                        int index = verticalIndex | column;
                        if (visited.get(index)) {
                            continue;
                        }
                        visited.set(index);
                        BlockState state = chunk.getBlockState(position.set(x, y, z));
                        Identifier blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        if (options.characteristicMaterial().test(blockKey.toString())) {
                            characteristicBlocks++;
                        }
                    }
                }
            }
        }

        int vegetationBlocks = 0;
        int vegetationColumns = 0;
        if (options.inspectVegetation()) {
            for (int column = 0; column < maximumPieceY.length; column++) {
                int highestPieceY = maximumPieceY[column];
                if (highestPieceY == Integer.MIN_VALUE || highestPieceY >= maximumWorldY) {
                    continue;
                }
                boolean vegetationColumn = false;
                int x = minimumChunkX + (column & 15);
                int z = minimumChunkZ + (column >> 4);
                for (int y = highestPieceY + 1; y <= maximumWorldY; y++) {
                    BlockState state = chunk.getBlockState(position.set(x, y, z));
                    boolean vegetation = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
                    if (!(vegetation && y > highestPieceY)) {
                        continue;
                    }
                    vegetationBlocks++;
                    vegetationColumn = true;
                }
                if (vegetationColumn) {
                    vegetationColumns++;
                }
            }
        }

        int foundationBaseColumns = 0;
        int foundationBlocks = 0;
        int foundationColumns = 0;
        int foundationGapColumns = 0;
        if (options.inspectFoundation()) {
            BoundingBox area = new BoundingBox(
                    minimumChunkX, minimumWorldY, minimumChunkZ,
                    maximumChunkX, maximumWorldY, maximumChunkZ);
            NativeStructureFoundationBuilder.StiltSupportAudit foundation =
                    NativeStructureFoundationBuilder.auditStiltSupport(
                            level, area, start, (BlockState) options.foundationMaterial().nativeHandle(), options.surfaceHeight());
            foundationBaseColumns = foundation.baseColumns();
            foundationBlocks = foundation.stiltBlocks();
            foundationColumns = foundation.stiltColumns();
            foundationGapColumns = foundation.unsupportedColumns();
        }

        return new StructureMaterialAudit(scanned, characteristicBlocks, vegetationBlocks,
                vegetationColumns, foundationBaseColumns, foundationBlocks, foundationColumns,
                foundationGapColumns);
    }

    private static List<ChunkPos> selectFootprintChunks(StructureStart start, int limit) {
        LinkedHashSet<ChunkPos> selected = new LinkedHashSet<>();
        addBounded(selected, start.getChunkPos(), limit);
        List<ChunkPos> pieceAnchors = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox bounds = piece.getBoundingBox();
            pieceAnchors.add(new ChunkPos((bounds.minX() + bounds.maxX()) >> 5,
                    (bounds.minZ() + bounds.maxZ()) >> 5));
            pieceAnchors.add(new ChunkPos(bounds.minX() >> 4, bounds.minZ() >> 4));
            pieceAnchors.add(new ChunkPos(bounds.maxX() >> 4, bounds.maxZ() >> 4));
        }
        pieceAnchors.sort(Comparator.comparingInt((ChunkPos chunkPos) ->
                chunkPos.distanceSquared(start.getChunkPos())));
        for (ChunkPos chunkPos : pieceAnchors) {
            addBounded(selected, chunkPos, limit);
        }
        for (ChunkPos chunkPos : boundedFootprintChunks(start.getBoundingBox(), start.getChunkPos(), limit)) {
            if (intersectsAnyPiece(start.getPieces(), chunkPos)) {
                addBounded(selected, chunkPos, limit);
            }
        }
        return List.copyOf(selected);
    }

    public static List<ChunkPos> boundedFootprintChunks(BoundingBox bounds, ChunkPos origin, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;
        long width = (long) maxChunkX - minChunkX + 1L;
        long depth = (long) maxChunkZ - minChunkZ + 1L;
        long total = width * depth;
        LinkedHashSet<ChunkPos> chunks = new LinkedHashSet<>();
        addBounded(chunks, origin, limit);
        if (total <= limit) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    addBounded(chunks, new ChunkPos(chunkX, chunkZ), limit);
                }
            }
            return List.copyOf(chunks);
        }
        addBounded(chunks, new ChunkPos(minChunkX, minChunkZ), limit);
        addBounded(chunks, new ChunkPos(maxChunkX, minChunkZ), limit);
        addBounded(chunks, new ChunkPos(minChunkX, maxChunkZ), limit);
        addBounded(chunks, new ChunkPos(maxChunkX, maxChunkZ), limit);
        int samplesPerAxis = Math.max(2, (int) Math.floor(Math.sqrt(limit)));
        for (int sampleZ = 0; sampleZ < samplesPerAxis; sampleZ++) {
            int chunkZ = sampleCoordinate(minChunkZ, maxChunkZ, sampleZ, samplesPerAxis);
            for (int sampleX = 0; sampleX < samplesPerAxis; sampleX++) {
                int chunkX = sampleCoordinate(minChunkX, maxChunkX, sampleX, samplesPerAxis);
                addBounded(chunks, new ChunkPos(chunkX, chunkZ), limit);
            }
        }
        return List.copyOf(chunks);
    }

    private static int footprintChunkCount(BoundingBox bounds) {
        long width = (long) (bounds.maxX() >> 4) - (bounds.minX() >> 4) + 1L;
        long depth = (long) (bounds.maxZ() >> 4) - (bounds.minZ() >> 4) + 1L;
        long total = width * depth;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static int sampleCoordinate(int minimum, int maximum, int index, int samples) {
        if (samples <= 1 || minimum == maximum) {
            return minimum;
        }
        double progress = (double) index / (double) (samples - 1);
        return minimum + (int) Math.round((maximum - minimum) * progress);
    }

    private static void addBounded(Set<ChunkPos> chunks, ChunkPos chunkPos, int limit) {
        if (chunks.size() < limit) {
            chunks.add(chunkPos);
        }
    }

    private static boolean intersectsAnyPiece(List<StructurePiece> pieces, ChunkPos chunkPos) {
        for (StructurePiece piece : pieces) {
            if (intersectsChunk(piece.getBoundingBox(), chunkPos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectsChunk(BoundingBox bounds, ChunkPos chunkPos) {
        return bounds.maxX() >= chunkPos.getMinBlockX()
                && bounds.minX() <= chunkPos.getMaxBlockX()
                && bounds.maxZ() >= chunkPos.getMinBlockZ()
                && bounds.minZ() <= chunkPos.getMaxBlockZ();
    }

    private static BlockEntityAudit auditBlockEntities(ServerLevel level, ChunkAccess chunk) {
        int states = 0;
        int present = 0;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(BlockState::hasBlockEntity)) {
                continue;
            }
            int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (!state.hasBlockEntity()) {
                            continue;
                        }
                        states++;
                        position.set(chunk.getPos().getBlockX(localX), sectionMinY + localY,
                                chunk.getPos().getBlockZ(localZ));
                        if (level.getBlockEntity(position) != null) {
                            present++;
                        }
                    }
                }
            }
        }
        return new BlockEntityAudit(states, present);
    }

    static PoiAudit auditStructurePois(ServerLevel level, StructureStart start) {
        int inBounds = 0;
        int outOfBounds = 0;
        PoiManager poiManager = level.getPoiManager();
        for (ChunkPos chunkPos : selectFootprintChunks(start, MAX_FOOTPRINT_CHUNKS)) {
            List<PoiRecord> records = poiManager.getInChunk(
                    holder -> true, chunkPos, PoiManager.Occupancy.ANY).toList();
            for (PoiRecord record : records) {
                BlockPos position = record.getPos();
                if (position.getY() < level.getMinY() || position.getY() >= level.getMaxY()) {
                    outOfBounds++;
                    continue;
                }
                if (insideAnyPiece(start.getPieces(), position)) {
                    inBounds++;
                }
            }
        }
        return new PoiAudit(inBounds, outOfBounds);
    }

    private static boolean insideAnyPiece(List<StructurePiece> pieces, BlockPos position) {
        for (StructurePiece piece : pieces) {
            if (piece.getBoundingBox().isInside(position)) {
                return true;
            }
        }
        return false;
    }

    public record FootprintAudit(int inspectedChunks, int availableChunks, int evidenceChunks,
                                  int coveredPieces, int totalPieces, int blockEntityStates,
                                  int blockEntitiesPresent, int materialScannedChunks,
                                  int characteristicBlocks, int characteristicChunks,
                                  int vegetationBlocks, int vegetationColumns,
                                  int foundationBaseColumns, int foundationBlocks, int foundationColumns,
                                  int foundationGapColumns) {
    }

    private record BlockEntityAudit(int states, int present) {
    }

    private record StructureMaterialAudit(boolean scanned, int characteristicBlocks,
                                          int vegetationBlocks, int vegetationColumns,
                                          int foundationBaseColumns, int foundationBlocks,
                                          int foundationColumns,
                                          int foundationGapColumns) {
    }

    public record PoiAudit(int inBounds, int outOfBounds) {
    }
}
