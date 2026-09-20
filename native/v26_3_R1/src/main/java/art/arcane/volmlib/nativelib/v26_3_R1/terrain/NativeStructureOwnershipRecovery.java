package art.arcane.volmlib.nativelib.v26_3_R1.terrain;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureRecoveryPolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferenceBounds;
import art.arcane.volmlib.nativelib.terrain.structure.StructureFingerprint;
import java.util.function.BiFunction;

import art.arcane.volmlib.nativelib.terrain.structure.StructureTerrainSettings;

import art.arcane.volmlib.util.structure.StructureTerrainMode;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.Locale;
import java.util.Objects;

public final class NativeStructureOwnershipRecovery {
    private NativeStructureOwnershipRecovery() {
    }

    public static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> O resolve(
            StructureRecoveryPolicy<P, O> policy, ServerLevel level, String structureKey,
            Structure structure, StructureStart start) {
        Objects.requireNonNull(policy, "Native structure ownership recovery requires a policy");
        ServerLevel activeLevel = Objects.requireNonNull(
                level, "Native structure ownership recovery requires a level");
        Structure activeStructure = Objects.requireNonNull(
                structure, "Native structure ownership recovery requires a structure");
        if (start == null || !start.isValid() || start.getStructure() != activeStructure) {
            return null;
        }
        ChunkPos origin = start.getChunkPos();
        O persisted = policy.findPersisted(structureKey, origin.x(), origin.z());
        if (persisted != null) {
            O refreshed = refreshReferenceEnvelope(
                    structureKey, activeStructure, start, persisted);
            if (refreshed != null) {
                if (refreshed != persisted) {
                    policy.record(refreshed);
                }
                return refreshed;
            }
            policy.discard(structureKey, origin.x(), origin.z());
        }
        P plan = policy.matchingPlan(structureKey, origin.x(), origin.z());
        if (!matchesPlan(structureKey, origin, plan)) {
            return null;
        }
        Registry<Structure> registry = activeLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = registry.wrapAsHolder(activeStructure);
        ChunkGeneratorStructureState state = activeLevel.getChunkSource().getGeneratorState();
        if (naturalStartIsAmbiguous(policy, structureKey, activeStructure, holder, state, plan)) {
            return null;
        }
        StructureStart expected = generateExpected(
                policy, activeLevel, holder, plan, start.getReferences());
        O recovered = proveCandidate(
                structureKey, activeStructure, start, plan, expected, false, policy::capture);
        if (recovered == null) {
            return null;
        }
        policy.record(recovered);
        return recovered;
    }

    public static <O extends StructureOwnershipRecordView<O>> O refreshReferenceEnvelope(
            String structureKey, Structure structure, StructureStart start,
            O ownership) {
        if (structure == null || start == null || !start.isValid()
                || start.getStructure() != structure || ownership == null
                || !ownership.structureKey().equals(normalize(structureKey))
                || !NativeStructureOwnershipFingerprint.matches(ownership, start)) {
            return null;
        }
        StructureTerrainSettings terrain = NativeStructureTerrainIntegrator.resolveNativeTerrain(
                start, ownership.terrain());
        if (terrain.resolvedMode() != StructureTerrainMode.VACUUM) {
            return ownership;
        }
        BoundingBox expected = NativeStructureReferenceEnvelope.referenceBounds(
                start, structure, terrain, structureKey);
        int referenceMinChunkX = expected.minX() >> 4;
        int referenceMaxChunkX = expected.maxX() >> 4;
        int referenceMinChunkZ = expected.minZ() >> 4;
        int referenceMaxChunkZ = expected.maxZ() >> 4;
        if (ownership.referenceMinChunkX() == referenceMinChunkX
                && ownership.referenceMaxChunkX() == referenceMaxChunkX
                && ownership.referenceMinChunkZ() == referenceMinChunkZ
                && ownership.referenceMaxChunkZ() == referenceMaxChunkZ) {
            return ownership;
        }
        return ownership.withReferenceEnvelope(new StructureReferenceBounds(
                referenceMinChunkX, referenceMaxChunkX,
                referenceMinChunkZ, referenceMaxChunkZ));
    }

    public static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> O proveCandidate(
            String structureKey, Structure structure, StructureStart persisted,
            P plan, StructureStart expected,
            boolean naturalStartAmbiguous, BiFunction<StructureFingerprint, P, O> ownershipFactory) {
        if (naturalStartAmbiguous || structure == null
                || persisted == null || !persisted.isValid()
                || expected == null || !expected.isValid()
                || persisted.getStructure() != structure
                || expected.getStructure() != structure
                || !matchesPlan(structureKey, persisted.getChunkPos(), plan)
                || !persisted.getChunkPos().equals(expected.getChunkPos())) {
            return null;
        }
        BoundingBox referenceEnvelope = NativeStructureReferenceEnvelope.referenceBounds(
                expected, structure, plan.terrain(), structureKey);
        O candidate = ownershipFactory.apply(
                NativeStructureOwnershipFingerprint.capture(
                        structureKey, expected, referenceEnvelope), plan);
        if (candidate.placementIdentity()
                != plan.placementIdentity()) {
            return null;
        }
        return NativeStructureOwnershipFingerprint.matches(candidate, persisted)
                ? candidate : null;
    }

    private static boolean matchesPlan(String structureKey, ChunkPos origin,
                                       StructureStartPlan plan) {
        if (structureKey == null || structureKey.isBlank() || plan == null) {
            return false;
        }
        return plan.chunkX() == origin.x()
                && plan.chunkZ() == origin.z()
                && normalize(structureKey).equals(normalize(plan.structureKey()));
    }

    private static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> StructureStart generateExpected(
            StructureRecoveryPolicy<P, O> policy, ServerLevel level, Holder<Structure> holder,
            P plan, int references) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        NativeStructureFactory.GenerationContext generationContext =
                new NativeStructureFactory.GenerationContext(
                        level.registryAccess(),
                        generator,
                        biomeSource,
                        state.randomState(),
                        level.getStructureTemplateManager(),
                        state.getLevelSeed(),
                        level.dimension(),
                        level,
                        biome -> true,
                        generator.getSeaLevel(),
                        policy::surfaceHeight
                );
        return NativeStructureFactory.generate(generationContext, holder, plan, references);
    }

    private static <P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> boolean naturalStartIsAmbiguous(
            StructureRecoveryPolicy<P, O> policy, String structureKey, Structure structure,
            Holder<Structure> holder, ChunkGeneratorStructureState state,
            P plan) {
        if (plan.replacesSource()) {
            return false;
        }
        if (policy.sourceReplaced(structureKey,
                NativeStructureVegetationClearer.isUndergroundStep(structure.step()))) {
            return false;
        }
        for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
            if (placement.isStructureChunk(state, plan.chunkX(), plan.chunkZ())) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String structureKey) {
        return structureKey == null ? "" : structureKey.trim().toLowerCase(Locale.ROOT);
    }
}
