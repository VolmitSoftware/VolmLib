package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.nativelib.terrain.structure.StructureVolumePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.util.collection.KList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;

public final class NativeStructureVolumeSource<C, P extends StructureStartPlan> {
    private final Context context;
    private final StructureVolumePolicy<C, P> policy;

    public NativeStructureVolumeSource(Context context, StructureVolumePolicy<C, P> policy) {
        this.context = Objects.requireNonNull(context, "Native structure volume context must not be null");
        this.policy = Objects.requireNonNull(policy, "Native structure volume policy must not be null");
    }

    public KList<NativeStructureVolume> volumesAt(C source, int chunkX, int chunkZ) {
        ChunkGeneratorStructureState state = context.structureState().get();
        ChunkGenerator generator = context.generator().get();
        BiomeSource biomeSource = context.biomeSource().get();
        if (state == null || generator == null || biomeSource == null) {
            return NativeStructureVolume.NONE;
        }
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Bindings bindings = new Bindings(state, generator, biomeSource);
        KList<NativeStructureVolume> volumes = appendPlannedVolumes(source, bindings, registry, chunkX, chunkZ, null);
        volumes = appendVanillaVolumes(source, bindings, registry, chunkX, chunkZ, volumes);
        return volumes == null ? NativeStructureVolume.NONE : volumes;
    }

    private KList<NativeStructureVolume> appendPlannedVolumes(C source, Bindings bindings,
                                                             Registry<Structure> registry, int chunkX, int chunkZ,
                                                             KList<NativeStructureVolume> volumes) {
        List<P> plans;
        try {
            plans = policy.plansAt(source, chunkX, chunkZ);
        } catch (RuntimeException | Error error) {
            policy.warn("iris-placements", error);
            return volumes;
        }

        KList<NativeStructureVolume> target = volumes;
        for (P plan : plans) {
            StructurePlacementDecision decision = policy.decisionFor(source, plan);
            if (!decision.generate()) {
                continue;
            }
            Identifier identifier = Identifier.tryParse(plan.structureKey());
            if (identifier == null) {
                continue;
            }
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                continue;
            }
            String structureId = identifier.toString();
            try {
                StructureStart generated = NativeStructureFactory.generate(
                        generationContext(source, bindings), registry.wrapAsHolder(structure), plan, 0);
                target = appendPieces(target, structureId, generated);
            } catch (RuntimeException | Error error) {
                policy.warn(structureId, error);
            }
        }
        return target;
    }

    private KList<NativeStructureVolume> appendVanillaVolumes(C source, Bindings bindings,
                                                             Registry<Structure> registry, int chunkX, int chunkZ,
                                                             KList<NativeStructureVolume> volumes) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        IntBinaryOperator surfaceHeight = surfaceHeight(source);
        int worldMinY = context.heightAccessor().getMinY();
        int worldMaxYExclusive = worldMinY + context.heightAccessor().getHeight();
        KList<NativeStructureVolume> target = volumes;
        for (Holder<StructureSet> setHolder : bindings.state().possibleStructureSets()) {
            StructureSet set = setHolder.value();
            if (!set.placement().isStructureChunk(bindings.state(), chunkX, chunkZ)) {
                continue;
            }
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Holder<Structure> holder = entry.structure();
                Structure structure = holder.value();
                Identifier identifier = registry.getKey(structure);
                if (identifier == null) {
                    continue;
                }
                String structureId = identifier.toString();
                boolean undergroundStep = NativeStructureVegetationClearer.isUndergroundStep(structure.step());
                try {
                    StructurePlacementDecision decision = policy.resolve(
                            source, structureId, undergroundStep);
                    if (!decision.generate()) {
                        continue;
                    }
                    StructureStart generated = structure.generate(
                            holder,
                            context.levelKey(),
                            context.registryAccess(),
                            bindings.generator(),
                            bindings.biomeSource(),
                            bindings.state().randomState(),
                            context.templateManager(),
                            bindings.state().getLevelSeed(),
                            chunkPos,
                            0,
                            context.heightAccessor(),
                            structure.biomes()::contains
                    );
                    if (generated == null || !generated.isValid()) {
                        continue;
                    }
                    NativeStructureVerticalPlacer.applyVerticalPlacement(
                            generated,
                            structureId,
                            decision.yShift(),
                            bindings.generator().getSeaLevel(),
                            worldMinY,
                            worldMaxYExclusive,
                            undergroundStep,
                            decision.preserveSourceY(),
                            decision.yBand(),
                            surfaceHeight);
                    target = appendPieces(target, structureId, generated);
                } catch (RuntimeException | Error error) {
                    policy.warn(structureId, error);
                }
            }
        }
        return target;
    }

    private NativeStructureFactory.GenerationContext generationContext(C source, Bindings bindings) {
        return new NativeStructureFactory.GenerationContext(
                context.registryAccess(),
                bindings.generator(),
                bindings.biomeSource(),
                bindings.state().randomState(),
                context.templateManager(),
                bindings.state().getLevelSeed(),
                context.levelKey(),
                context.heightAccessor(),
                biome -> true,
                bindings.generator().getSeaLevel(),
                surfaceHeight(source)
        );
    }

    private record Bindings(ChunkGeneratorStructureState state, ChunkGenerator generator, BiomeSource biomeSource) {
    }

    public static KList<NativeStructureVolume> appendPieces(KList<NativeStructureVolume> volumes,
                                                     String structureId, StructureStart start) {
        if (start == null || !start.isValid()) {
            return volumes;
        }
        KList<NativeStructureVolume> target = volumes;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox bounds = piece.getBoundingBox();
            if (target == null) {
                target = new KList<>();
            }
            target.add(new NativeStructureVolume(structureId,
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        }
        return target;
    }

    private IntBinaryOperator surfaceHeight(C source) {
        return (x, z) -> policy.surfaceHeight(source, x, z);
    }

    /**
     * The generator, biome source and structure state arrive as suppliers so an installed index never holds the
     * level (and through it the engine) strongly. That keeps the weakly keyed registry collectable on world unload.
     */
    public record Context(
            RegistryAccess registryAccess,
            StructureTemplateManager templateManager,
            ResourceKey<Level> levelKey,
            LevelHeightAccessor heightAccessor,
            Supplier<ChunkGenerator> generator,
            Supplier<BiomeSource> biomeSource,
            Supplier<ChunkGeneratorStructureState> structureState
    ) {
    }
}
