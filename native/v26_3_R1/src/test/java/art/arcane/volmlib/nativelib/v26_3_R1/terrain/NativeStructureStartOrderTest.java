package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureStartOrderTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void persistedReferencesKeepPlacementSeedsAndOverwriteOrder() {
        ChunkPos first = new ChunkPos(-8, -8);
        ChunkPos second = new ChunkPos(-8, -6);
        LongSet fresh = new LongOpenHashSet();
        fresh.add(first.pack());
        fresh.add(second.pack());
        CompoundTag storage = new CompoundTag();
        storage.putLongArray("References", fresh.toLongArray());
        LongSet restored = new LongOpenHashSet(storage.getLongArray("References").orElseThrow());
        assertFalse("Reference round trip must exercise changed iteration order",
                Arrays.equals(fresh.toLongArray(), restored.toLongArray()));

        List<StructureStart> before = NativeBukkitStructureStage.orderedStarts(starts(fresh));
        List<StructureStart> after = NativeBukkitStructureStage.orderedStarts(starts(restored));
        assertEquals(List.of(first, second), origins(before));
        assertEquals(origins(before), origins(after));
        assertEquals(placementSeeds(before), placementSeeds(after));
    }

    @Test
    public void placementOrderIsStableForPermutationsOfNegativeAndPositiveOrigins() {
        StructureStart west = start(new ChunkPos(-3, 7));
        StructureStart north = start(new ChunkPos(2, -5));
        StructureStart south = start(new ChunkPos(2, 4));
        List<StructureStart> expected = NativeBukkitStructureStage.orderedStarts(List.of(west, north, south));
        for (List<StructureStart> input : List.of(List.of(south, north, west), List.of(north, west, south),
                List.of(west, south, north))) {
            List<StructureStart> actual = NativeBukkitStructureStage.orderedStarts(input);
            assertEquals(List.of(west.getChunkPos(), north.getChunkPos(), south.getChunkPos()), origins(actual));
            assertEquals(placementSeeds(expected), placementSeeds(actual));
        }
    }

    private static List<StructureStart> starts(LongSet references) {
        List<StructureStart> starts = new ArrayList<>(references.size());
        for (long reference : references) {
            starts.add(start(ChunkPos.unpack(reference)));
        }
        return starts;
    }

    private static StructureStart start(ChunkPos origin) {
        StructureStart start = mock(StructureStart.class);
        when(start.getChunkPos()).thenReturn(origin);
        return start;
    }

    private static List<ChunkPos> origins(List<StructureStart> starts) {
        List<ChunkPos> origins = new ArrayList<>(starts.size());
        for (StructureStart start : starts) {
            origins.add(start.getChunkPos());
        }
        return origins;
    }

    private static Map<ChunkPos, Long> placementSeeds(List<StructureStart> starts) {
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        long decorationSeed = random.setDecorationSeed(1248712L, -16, -16);
        random.setFeatureSeed(decorationSeed, 2, 3);
        Map<ChunkPos, Long> assigned = new LinkedHashMap<>(starts.size());
        for (StructureStart start : starts) {
            assigned.put(start.getChunkPos(), random.nextLong());
        }
        return assigned;
    }
}
