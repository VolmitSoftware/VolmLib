package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NativeSpawnTableMerger {
    private NativeSpawnTableMerger() {
    }

    public static WeightedList<MobSpawnSettings.SpawnerData> merge(
            WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns,
            WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns) {
        List<Weighted<MobSpawnSettings.SpawnerData>> entries = new ArrayList<>(
                vanillaSpawns.unwrap().size() + explicitSpawns.unwrap().size());
        Set<EntityType<?>> explicitTypes = new HashSet<>();
        for (Weighted<MobSpawnSettings.SpawnerData> entry : explicitSpawns.unwrap()) {
            explicitTypes.add(entry.value().type());
        }
        for (Weighted<MobSpawnSettings.SpawnerData> entry : vanillaSpawns.unwrap()) {
            if (!explicitTypes.contains(entry.value().type())) {
                entries.add(entry);
            }
        }
        entries.addAll(explicitSpawns.unwrap());
        return WeightedList.of(entries);
    }
}
