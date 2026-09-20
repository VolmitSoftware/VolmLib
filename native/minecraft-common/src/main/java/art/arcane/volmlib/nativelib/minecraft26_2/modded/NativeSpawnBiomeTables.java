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

import art.arcane.volmlib.nativelib.terrain.NativeSpawnBiomePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnTableMerger;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeSpawnBiomeTables<C> {
    private final NativeSpawnBiomePolicy<C> policy;
    private final Object owner;
    private final ConcurrentHashMap<SpawnBiomeKey, Holder<Biome>> vanillaSpawnBiomes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SpawnTableKey, WeightedList<MobSpawnSettings.SpawnerData>> mergedSpawnTables =
            new ConcurrentHashMap<>();
    private final Set<Integer> initializedRuntimeIdentities = ConcurrentHashMap.newKeySet();

    public NativeSpawnBiomeTables(NativeSpawnBiomePolicy<C> policy, Object owner) {
        this.policy = policy;
        this.owner = owner;
    }

    public void initializeVanillaSpawnBiomes(Registry<Biome> registry) {
        C current = policy.current();
        if (current == null) {
            return;
        }
        int runtimeIdentity = policy.runtimeId(current);
        if (initializedRuntimeIdentities.contains(runtimeIdentity)) {
            return;
        }
        synchronized (owner) {
            if (initializedRuntimeIdentities.contains(runtimeIdentity)) {
                return;
            }

            try (NativeGenerationLease lease = policy.lease(current);
                 NativeGenerationScope ignored = policy.context(current, lease)) {
                policy.populate(current, vanillaKey -> {
                    Holder<Biome> vanilla = resolveBiomeHolder(registry, vanillaKey);
                    if (vanilla == null) { return null; }
                    return customKey -> {
                        Holder<Biome> custom = resolveBiomeHolder(registry, customKey);
                        if (custom != null) {
                            vanillaSpawnBiomes.putIfAbsent(new SpawnBiomeKey(runtimeIdentity, custom.value()), vanilla);
                        }
                    };
                });
                initializedRuntimeIdentities.add(runtimeIdentity);
            }
        }
    }

    public Holder<Biome> vanillaSpawnBiome(Biome biome) {
        C current = policy.current();
        return current == null
                ? null
                : vanillaSpawnBiomes.get(new SpawnBiomeKey(policy.runtimeId(current), biome));
    }

    public WeightedList<MobSpawnSettings.SpawnerData> mergedSpawnTable(
            int runtimeId,
            Biome biome,
            Biome vanillaBiome,
            MobCategory category,
            WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns,
            WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns) {
        SpawnTableKey key = new SpawnTableKey(runtimeId, biome, vanillaBiome, category);
        return mergedSpawnTables.computeIfAbsent(
                key,
                ignored -> NativeSpawnTableMerger.merge(vanillaSpawns, explicitSpawns)
        );
    }

    public void evictRuntime(int runtimeIdentity) {
        vanillaSpawnBiomes.keySet().removeIf(key -> key.runtimeIdentity() == runtimeIdentity);
        mergedSpawnTables.keySet().removeIf(key -> key.runtimeIdentity() == runtimeIdentity);
        initializedRuntimeIdentities.remove(runtimeIdentity);
    }

    public Holder<Biome> resolveBiomeHolder(Registry<Biome> registry, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null) {
            return null;
        }
        Optional<Holder.Reference<Biome>> reference = registry.get(identifier);
        return reference.<Holder<Biome>>map((Holder.Reference<Biome> value) -> value).orElse(null);
    }

    public void resetVanillaSpawnBiomes() {
        synchronized (owner) {
            vanillaSpawnBiomes.clear();
            mergedSpawnTables.clear();
            initializedRuntimeIdentities.clear();
        }
    }

    private record SpawnBiomeKey(int runtimeIdentity, Biome biome) {
    }

    private record SpawnTableKey(int runtimeIdentity, Biome biome, Biome vanillaBiome, MobCategory category) {
    }
}
