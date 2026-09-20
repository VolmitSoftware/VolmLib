package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureInjectionPolicy;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeStructureStartInjector {
    private static final Set<String> WARNED_DUPLICATE_STRUCTURES = ConcurrentHashMap.newKeySet();

    private NativeStructureStartInjector() {
    }

    public static <P extends StructureStartPlan> Map<Structure, P> inject(InjectionContext<P> context) {
        Objects.requireNonNull(context, "Native structure injection context must not be null");
        ChunkAccess chunk = context.chunk();
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        SectionPos section = SectionPos.bottomOf(chunk);
        Map<Structure, P> configuredStarts = new LinkedHashMap<>();
        for (P plan : context.policy().plansAt(chunk.getPos().x(), chunk.getPos().z())) {
            Identifier identifier = Identifier.tryParse(plan.structureKey());
            if (identifier == null) {
                throw new IllegalStateException("Configured native structure key is invalid: "
                        + plan.structureKey());
            }
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                throw new IllegalStateException("Configured native structure is not registered: " + identifier);
            }
            Holder<Structure> holder = registry.wrapAsHolder(structure);
            if (configuredStarts.containsKey(structure)) {
                if (WARNED_DUPLICATE_STRUCTURES.add(identifier.toString())) {
                    context.policy().duplicate(identifier.toString());
                }
                continue;
            }
            StructureStart existing = context.structureManager().getStartForStructure(
                    section, structure, chunk);
            boolean replacement = plan.replacesSource();
            if (!replacement && existing != null && existing.isValid()) {
                replacement = context.policy().sourceReplaced(identifier.toString(),
                        NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
            }
            if (!replacement && existing != null && existing.isValid()) {
                continue;
            }
            int references = existing != null && existing.isValid() ? existing.getReferences() : 0;
            NativeStructureFactory.GenerationContext generationContext =
                    new NativeStructureFactory.GenerationContext(
                            context.registryAccess(),
                            context.generator(),
                            context.biomeSource(),
                            context.structureState().randomState(),
                            context.templateManager(),
                            context.structureState().getLevelSeed(),
                            context.levelKey(),
                            chunk,
                            biome -> true,
                            context.generator().getSeaLevel(),
                            context.policy()::surfaceHeight
                    );
            StructureStart generated = NativeStructureFactory.generate(
                    generationContext, holder, plan, references);
            if (!isUsableGeneratedStart(generated)) {
                if (replacement) {
                    context.structureManager().setStartForStructure(
                            section, structure, StructureStart.INVALID_START, chunk);
                }
                context.policy().discard(
                        identifier.toString(),
                        chunk.getPos().x(), chunk.getPos().z());
                continue;
            }
            BoundingBox referenceBounds = NativeStructureReferenceEnvelope.referenceBounds(
                    generated, structure, plan.terrain(), identifier.toString());
            context.policy().record(plan, NativeStructureOwnershipFingerprint.capture(
                    identifier.toString(), generated, referenceBounds));
            try {
                context.structureManager().setStartForStructure(
                        section, structure, generated, chunk);
            } catch (RuntimeException | Error publicationError) {
                try {
                    context.policy().discard(
                        identifier.toString(),
                            chunk.getPos().x(), chunk.getPos().z());
                } catch (RuntimeException | Error cleanupError) {
                    publicationError.addSuppressed(cleanupError);
                }
                throw publicationError;
            }
            configuredStarts.put(structure, plan);
        }
        return Map.copyOf(configuredStarts);
    }

    public static boolean isUsableGeneratedStart(StructureStart start) {
        return start != null && start.isValid();
    }

    public record InjectionContext<P extends StructureStartPlan>(
            StructureInjectionPolicy<P> policy,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            ResourceKey<Level> levelKey,
            ChunkGenerator generator,
            BiomeSource biomeSource
    ) {
    }
}
