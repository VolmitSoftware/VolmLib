/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeStructureFrequencyScale;
import art.arcane.volmlib.nativelib.terrain.StructureFrequencyControl;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class NativeStructureSetFrequencyOverrides {
    private NativeStructureSetFrequencyOverrides() {
    }

    public static ChunkGeneratorStructureState apply(
            ChunkGeneratorStructureState state,
            StructureFrequencyControl importedStructures
    ) {
        if (!importedStructures.hasFrequencyOverrides()) {
            return state;
        }
        List<Holder<StructureSet>> original = state.possibleStructureSets();
        List<Holder<StructureSet>> scaled = scaleSets(original, importedStructures);
        if (scaled == original) {
            return state;
        }
        Field possibleSetsField = declaredField(ChunkGeneratorStructureState.class, List.class);
        try {
            possibleSetsField.set(state, scaled);
            return state;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(
                    "Could not apply native structure-set frequency overrides", error);
        }
    }

    public static List<Holder<StructureSet>> scaleSets(
            List<Holder<StructureSet>> structureSets,
            StructureFrequencyControl importedStructures
    ) {
        Map<String, Holder<StructureSet>> holdersByKey = new HashMap<>();
        for (Holder<StructureSet> holder : structureSets) {
            String key = structureSetKey(holder);
            if (key != null) {
                holdersByKey.putIfAbsent(key, holder);
            }
        }
        Map<Holder<StructureSet>, Holder<StructureSet>> scaledByIdentity = new IdentityHashMap<>();
        List<Holder<StructureSet>> scaledSets = new ArrayList<>(structureSets.size());
        boolean changed = false;
        for (Holder<StructureSet> holder : structureSets) {
            Holder<StructureSet> scaled = scaleHolder(
                    holder, importedStructures, holdersByKey, scaledByIdentity);
            scaledSets.add(scaled);
            changed |= scaled != holder;
        }
        return changed ? List.copyOf(scaledSets) : structureSets;
    }

    private static Holder<StructureSet> scaleHolder(
            Holder<StructureSet> holder,
            StructureFrequencyControl importedStructures,
            Map<String, Holder<StructureSet>> holdersByKey,
            Map<Holder<StructureSet>, Holder<StructureSet>> scaledByIdentity
    ) {
        if (scaledByIdentity.containsKey(holder)) {
            return scaledByIdentity.get(holder);
        }
        if (!dependsOnOverride(holder, importedStructures, holdersByKey)) {
            scaledByIdentity.put(holder, holder);
            return holder;
        }
        ResourceKey<StructureSet> holderKey = holder.unwrapKey().orElseThrow(() ->
                new IllegalStateException("An affected native structure-set exclusion graph has an unkeyed holder"));
        ScaledStructureSetHolder scaledHolder = new ScaledStructureSetHolder(holderKey);
        scaledByIdentity.put(holder, scaledHolder);

        StructureSet originalSet = holder.value();
        StructurePlacement originalPlacement = originalSet.placement();
        Optional<AbstractSpreadingStructurePlacement.ExclusionZone> originalZone = exclusionZone(originalPlacement);
        Optional<AbstractSpreadingStructurePlacement.ExclusionZone> scaledZone = originalZone;
        if (originalZone.isPresent()) {
            Holder<StructureSet> target = canonicalHolder(
                    originalZone.get().otherSet(), holdersByKey);
            Holder<StructureSet> scaledTarget = scaleHolder(
                    target, importedStructures, holdersByKey, scaledByIdentity);
            if (scaledTarget != originalZone.get().otherSet()) {
                scaledZone = Optional.of(new AbstractSpreadingStructurePlacement.ExclusionZone(
                        scaledTarget, originalZone.get().chunkCount()));
            }
        }
        double multiplier = importedStructures.frequencyMultiplier(structureSetKey(holder));
        StructurePlacement scaledPlacement = multiplier == 1D && scaledZone.equals(originalZone)
                ? originalPlacement
                : scalePlacement(originalPlacement, originalZone, scaledZone, multiplier);
        scaledHolder.bind(new StructureSet(originalSet.structures(), scaledPlacement));
        return scaledHolder;
    }

    private static boolean dependsOnOverride(
            Holder<StructureSet> holder,
            StructureFrequencyControl importedStructures,
            Map<String, Holder<StructureSet>> holdersByKey
    ) {
        Set<Holder<StructureSet>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Holder<StructureSet> current = holder;
        while (visited.add(current)) {
            if (importedStructures.frequencyMultiplier(structureSetKey(current)) != 1D) {
                return true;
            }
            Optional<AbstractSpreadingStructurePlacement.ExclusionZone> zone =
                    exclusionZone(current.value().placement());
            if (zone.isEmpty()) {
                return false;
            }
            current = canonicalHolder(zone.get().otherSet(), holdersByKey);
        }
        return false;
    }

    private static Holder<StructureSet> canonicalHolder(
            Holder<StructureSet> holder,
            Map<String, Holder<StructureSet>> holdersByKey
    ) {
        String key = structureSetKey(holder);
        return key == null ? holder : holdersByKey.getOrDefault(key, holder);
    }

    private static StructurePlacement scalePlacement(
            StructurePlacement placement,
            Optional<AbstractSpreadingStructurePlacement.ExclusionZone> originalZone,
            Optional<AbstractSpreadingStructurePlacement.ExclusionZone> scaledZone,
            double multiplier
    ) {
        Vec3i locateOffset = (Vec3i) declaredFieldValue(
                AbstractSpreadingStructurePlacement.class, placement, Vec3i.class);
        AbstractSpreadingStructurePlacement.FrequencyReductionMethod reductionMethod =
                (AbstractSpreadingStructurePlacement.FrequencyReductionMethod) declaredFieldValue(
                        AbstractSpreadingStructurePlacement.class,
                        placement,
                        AbstractSpreadingStructurePlacement.FrequencyReductionMethod.class);
        float frequency = (float) declaredFieldValue(
                AbstractSpreadingStructurePlacement.class, placement, float.class);
        int salt = (int) declaredFieldValue(
                AbstractSpreadingStructurePlacement.class, placement, int.class);

        if (placement.getClass() == RandomSpreadStructurePlacement.class) {
            RandomSpreadStructurePlacement randomSpread =
                    (RandomSpreadStructurePlacement) placement;
            NativeStructureFrequencyScale scale = NativeStructureFrequencyScale.randomSpread(
                    frequency, randomSpread.spacing(), randomSpread.separation(), multiplier);
            if (scale.frequency() == frequency
                    && scale.spacing() == randomSpread.spacing()
                    && scaledZone.equals(originalZone)) {
                return placement;
            }
            return new RandomSpreadStructurePlacement(
                    locateOffset,
                    reductionMethod,
                    scale.frequency(),
                    salt,
                    scaledZone,
                    scale.spacing(),
                    randomSpread.separation(),
                    randomSpread.spreadType());
        }
        if (placement.getClass() == ConcentricRingsStructurePlacement.class) {
            ConcentricRingsStructurePlacement rings =
                    (ConcentricRingsStructurePlacement) placement;
            float scaledFrequency = NativeStructureFrequencyScale.probability(frequency, multiplier);
            if (scaledFrequency == frequency && scaledZone.equals(originalZone)) {
                return placement;
            }
            return new ConcentricRingsStructurePlacement(
                    locateOffset,
                    reductionMethod,
                    scaledFrequency,
                    salt,
                    scaledZone,
                    rings.distance(),
                    rings.spread(),
                    rings.count(),
                    rings.preferredBiomes());
        }
        throw new IllegalStateException("Unsupported native structure placement: "
                + placement.getClass().getName());
    }

    private static Optional<AbstractSpreadingStructurePlacement.ExclusionZone> exclusionZone(
            StructurePlacement placement
    ) {
        if (!(placement instanceof AbstractSpreadingStructurePlacement)) {
            return Optional.empty();
        }
        Object value = declaredFieldValue(AbstractSpreadingStructurePlacement.class, placement, Optional.class);
        if (value instanceof Optional<?> optional && (optional.isEmpty()
                || optional.get() instanceof AbstractSpreadingStructurePlacement.ExclusionZone)) {
            @SuppressWarnings("unchecked")
            Optional<AbstractSpreadingStructurePlacement.ExclusionZone> resolved =
                    (Optional<AbstractSpreadingStructurePlacement.ExclusionZone>) optional;
            return resolved;
        }
        throw new IllegalStateException("Could not read native structure exclusion zone from "
                + placement.getClass().getName());
    }

    private static String structureSetKey(Holder<StructureSet> holder) {
        Optional<ResourceKey<StructureSet>> key = holder.unwrapKey();
        return key.map(resourceKey -> resourceKey.identifier().toString()).orElse(null);
    }

    private static Object declaredFieldValue(
            Class<?> declaringType,
            Object target,
            Class<?> fieldType
    ) {
        Field field = declaredField(declaringType, fieldType);
        try {
            return field.get(target);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not read " + fieldType.getName()
                    + " field on " + declaringType.getName(), error);
        }
    }

    private static Field declaredField(Class<?> declaringType, Class<?> fieldType) {
        Field match = null;
        for (Field field : declaringType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != fieldType) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException("Ambiguous " + fieldType.getName() + " field on "
                        + declaringType.getName());
            }
            match = field;
        }
        if (match == null || !match.trySetAccessible()) {
            throw new IllegalStateException("Missing accessible " + fieldType.getName()
                    + " field on " + declaringType.getName());
        }
        return match;
    }

    private static final class ScaledStructureSetHolder extends Holder.Reference<StructureSet> {
        private ScaledStructureSetHolder(ResourceKey<StructureSet> key) {
            super(Type.STAND_ALONE, new HolderOwner<>() {
            }, key, null);
        }

        private void bind(StructureSet structureSet) {
            bindValue(structureSet);
        }
    }
}
