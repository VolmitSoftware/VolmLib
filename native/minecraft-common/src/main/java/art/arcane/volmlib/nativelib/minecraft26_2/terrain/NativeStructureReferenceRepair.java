package art.arcane.volmlib.nativelib.minecraft26_2.terrain;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NativeStructureReferenceRepair {
    private static final int REFERENCE_DISTANCE_CHUNKS =
            StructureOwnershipRecordView.MAX_REFERENCE_DISTANCE_CHUNKS;

    private NativeStructureReferenceRepair() {
    }

    public static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> void createReferences(StructureReferencePolicy<P, O> policy, WorldGenLevel level,
                                        StructureManager structureManager, ChunkAccess targetChunk) {
        ChunkPos target = targetChunk.getPos();
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ServerLevel serverLevel = level.getLevel();
        List<ScannedStart<O>> starts = new ArrayList<>();
        for (int originChunkX = target.x() - REFERENCE_DISTANCE_CHUNKS;
             originChunkX <= target.x() + REFERENCE_DISTANCE_CHUNKS; originChunkX++) {
            for (int originChunkZ = target.z() - REFERENCE_DISTANCE_CHUNKS;
                 originChunkZ <= target.z() + REFERENCE_DISTANCE_CHUNKS; originChunkZ++) {
                try (NativeGenerationScope ignored =
                             policy.openOriginScope(originChunkX, originChunkZ)) {
                    ChunkAccess originChunk = level.getChunk(
                            originChunkX, originChunkZ, ChunkStatus.STRUCTURE_STARTS);
                    for (Map.Entry<Structure, StructureStart> entry : originChunk.getAllStarts().entrySet()) {
                        ScannedStart<O> start = scanStart(
                                policy, serverLevel, structureManager, targetChunk, registry,
                                originChunk, entry.getKey(), entry.getValue());
                        if (start != null && isTargetRelevant(policy, targetChunk, start)) {
                            starts.add(start);
                        }
                    }
                }
            }
        }
        for (ScannedStart<O> start : starts) {
            boolean valid;
            synchronized (start.originChunk()) {
                StructureStart current = start.originChunk().getStartForStructure(start.structure());
                valid = current == start.start() && current.isValid();
            }
            if (!valid) {
                continue;
            }
            structureManager.addReferenceForStructure(
                    SectionPos.bottomOf(targetChunk), start.structure(),
                    start.origin().pack(), targetChunk);
        }
    }

    private static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> boolean isTargetRelevant(
            StructureReferencePolicy<P, O> policy, ChunkAccess targetChunk, ScannedStart<O> scanned) {
        StructureStart start = scanned.start();
        Structure structure = scanned.structure();
        ChunkPos target = targetChunk.getPos();
        if (scanned.ownership() != null) {
            return requiresReference(target, scanned.structureKey(), start, scanned.ownership());
        }
        if (scanned.registered()) {
            return requiresNaturalReference(
                    policy, target, scanned.structureKey(), structure, start);
        }
        return NativeStructureReferenceEnvelope.contentBounds(start).intersects(
                target.getMinBlockX(), target.getMinBlockZ(),
                target.getMaxBlockX(), target.getMaxBlockZ());
    }

    private static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> ScannedStart<O> scanStart(
            StructureReferencePolicy<P, O> policy, ServerLevel level, StructureManager structureManager,
            ChunkAccess targetChunk, Registry<Structure> registry,
            ChunkAccess originChunk, Structure structure, StructureStart start) {
        Identifier identifier = registry.getKey(structure);
        String structureKey = identifier == null
                ? structure.getClass().getName() : identifier.toString();
        if (start == null || !start.isValid()) {
            return null;
        }
        if (!NativeStructureReferenceEnvelope.contentFitsReferenceRange(start)) {
            if (identifier != null) {
                policy.discard(structureKey, start.getChunkPos().x(), start.getChunkPos().z());
            }
            invalidateStart(structureManager, originChunk, structure, start, targetChunk);
            return null;
        }
        ChunkPos startOrigin = start.getChunkPos();
        O ownership = identifier == null ? null
                : NativeStructureOwnershipRecovery.resolve(
                    policy, level, structureKey, structure, start);
        if (ownership != null) {
            return new ScannedStart<>(
                    originChunk, structure, start, structureKey, ownership, true);
        }
        if (identifier != null && !naturalPolicyAllows(policy, structureKey, structure)) {
            invalidateStart(structureManager, originChunk, structure, start, targetChunk);
            policy.invalidated(structureKey);
            return null;
        }
        return new ScannedStart<>(
                originChunk, structure, start, structureKey, null, identifier != null);
    }

    private static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> boolean naturalPolicyAllows(
            StructureReferencePolicy<P, O> policy, String structureKey, Structure structure) {
        StructurePlacementDecision decision = policy.resolve(structureKey,
                NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
        return naturalDecisionAllows(decision);
    }

    public static boolean naturalDecisionAllows(StructurePlacementDecision decision) {
        return Objects.requireNonNull(
                decision, "Native structure decision must not be null").generate();
    }

    private static void invalidateStart(
            StructureManager structureManager, ChunkAccess originChunk,
            Structure structure, StructureStart start, ChunkAccess targetChunk) {
        synchronized (originChunk) {
            StructureStart current = originChunk.getStartForStructure(structure);
            if (current == start && current.isValid()) {
                structureManager.setStartForStructure(
                        SectionPos.bottomOf(originChunk), structure,
                        StructureStart.INVALID_START, originChunk);
            }
        }
        if (targetChunk != null) {
            removeTargetReference(targetChunk, structure, start.getChunkPos().pack());
        }
    }

    private static void removeTargetReference(
            ChunkAccess targetChunk, Structure structure, long origin) {
        LongSet current = targetChunk.getAllReferences().get(structure);
        if (current == null || !current.contains(origin)) {
            return;
        }
        Map<Structure, LongSet> updated = new HashMap<>(targetChunk.getAllReferences());
        LongSet retained = new LongOpenHashSet(current);
        retained.remove(origin);
        if (retained.isEmpty()) {
            updated.remove(structure);
        } else {
            updated.put(structure, retained);
        }
        targetChunk.setAllReferences(updated);
    }

    public static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> boolean requiresNaturalReference(StructureReferencePolicy<P, O> policy, ChunkPos target, String structureKey,
                                            Structure structure, StructureStart start) {
        if (target == null || start == null || !start.isValid()) {
            return false;
        }
        StructurePlacementDecision decision = policy.resolve(structureKey,
                NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
        if (!decision.generate()) {
            return false;
        }
        BoundingBox referenceBounds = NativeStructureReferenceEnvelope.referenceBounds(
                start, structure,
                NativeStructureTerrainIntegrator.resolveNativeTerrain(start, decision.terrain()),
                structureKey);
        return referenceBounds.intersects(
                target.getMinBlockX(), target.getMinBlockZ(),
                target.getMaxBlockX(), target.getMaxBlockZ());
    }

    public static boolean requiresReference(ChunkPos target, String structureKey,
                                     StructureStart start,
                                     StructureOwnershipRecordView<?> ownership) {
        if (target == null || start == null || !start.isValid()
                || ownership == null || !ownership.structureKey().equals(structureKey)
                || !ownership.covers(target.x(), target.z())) {
            return false;
        }
        return NativeStructureOwnershipFingerprint.matches(ownership, start);
    }

    private record ScannedStart<O extends StructureOwnershipRecordView<O>>(
            ChunkAccess originChunk,
            Structure structure,
            StructureStart start,
            String structureKey,
            O ownership,
            boolean registered
    ) {
        private ChunkPos origin() {
            return ownership == null
                    ? start.getChunkPos()
                    : new ChunkPos(ownership.originChunkX(), ownership.originChunkZ());
        }
    }
}
