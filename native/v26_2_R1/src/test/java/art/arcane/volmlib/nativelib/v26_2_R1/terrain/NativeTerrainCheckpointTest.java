package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.SnapshotPolicy;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

public class NativeTerrainCheckpointTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void checkpointReadsTheSuppliedMetadataKeys() {
        SnapshotPolicy<?> policy = mock(SnapshotPolicy.class);
        when(policy.receiptKey()).thenReturn("terrain:receipt");
        when(policy.activationKey()).thenReturn("terrain:activation");
        byte[] receipt = new byte[]{7, 8};
        CompoundTag values = new CompoundTag();
        values.putByteArray("terrain:receipt", receipt);
        values.putLong("terrain:activation", 19L);
        CompoundTag data = new CompoundTag();
        data.putString("Status", "minecraft:full");
        data.put("ChunkBukkitValues", values);

        NativeTerrainSnapshotsImpl.Checkpoint checkpoint = NativeTerrainSnapshotsImpl.checkpoint(2, -4, data, policy);

        assertEquals(new ChunkPos(2, -4), checkpoint.chunk());
        assertEquals("minecraft:full", checkpoint.verification().status());
        assertArrayEquals(receipt, checkpoint.verification().receipt());
        assertEquals(19L, checkpoint.verification().structureActivation());
    }

    @Test
    public void checkpointWaitsForDirtyAndUnloadedChunkWritesBeforeFlushAndVerification() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        SnapshotPolicy<?> policy = mock(SnapshotPolicy.class);
        Path root = Path.of("checkpoint-world");
        byte[] receipt = new byte[]{1, 2, 3};
        NativeTerrainSnapshotsImpl.Checkpoint saved = new NativeTerrainSnapshotsImpl.Checkpoint(new ChunkPos(4, -5),
                new NativeTerrainSnapshotsImpl.Verification("minecraft:full", receipt, 7L));
        NativeTerrainSnapshotsImpl.Checkpoint unloaded = NativeTerrainSnapshotsImpl.checkpoint(-8, 9, null, policy);
        Set<ChunkPos> completed = new HashSet<>();
        AtomicBoolean flushed = new AtomicBoolean();
        try (MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class)) {
            io.when(() -> MoonriseRegionFileIO.getPriority(level, 4, -5, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenAnswer(invocation -> {
                        completed.add(saved.chunk());
                        return Priority.COMPLETING;
                    });
            io.when(() -> MoonriseRegionFileIO.getPriority(level, -8, 9, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenAnswer(invocation -> {
                        completed.add(unloaded.chunk());
                        return Priority.COMPLETING;
                    });
            io.when(() -> MoonriseRegionFileIO.flushRegionStorages(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenAnswer(invocation -> {
                        assertEquals(Set.of(saved.chunk(), unloaded.chunk()), completed);
                        flushed.set(true);
                        return null;
                    });
            doAnswer(invocation -> {
                        assertTrue(flushed.get());
                        return null;
                    }).when(policy).verifyCheckpoint(root, new SnapshotPolicy.CheckpointData(4, -5, "minecraft:full", receipt, 7L));

            NativeTerrainSnapshotsImpl.finishCheckpoint(level, root, List.of(saved, unloaded), policy);

            verify(policy).verifyCheckpoint(root, new SnapshotPolicy.CheckpointData(4, -5, "minecraft:full", receipt, 7L));
            io.verify(() -> MoonriseRegionFileIO.flush(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA), never());
            io.verify(() -> MoonriseRegionFileIO.getPriority(level, 100, 100, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA), never());
            verifyNoMoreInteractions(policy);
        }
    }

    @Test
    public void storageFlushFailureCannotCompleteTheCheckpoint() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        SnapshotPolicy<?> policy = mock(SnapshotPolicy.class);
        IOException failure = new IOException("storage flush failed");
        try (MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class)) {
            io.when(() -> MoonriseRegionFileIO.flushRegionStorages(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenThrow(failure);

            assertSame(failure, assertThrows(IOException.class,
                    () -> NativeTerrainSnapshotsImpl.finishCheckpoint(level, Path.of("checkpoint-world"), List.of(), policy)));
            verifyNoInteractions(policy);
        }
    }

    @Test
    public void persistedReceiptMismatchCannotCompleteTheCheckpoint() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        SnapshotPolicy<?> policy = mock(SnapshotPolicy.class);
        Path root = Path.of("checkpoint-world");
        byte[] receipt = new byte[]{4, 5};
        IOException failure = new IOException("saved terrain receipt mismatch");
        NativeTerrainSnapshotsImpl.Checkpoint saved = new NativeTerrainSnapshotsImpl.Checkpoint(new ChunkPos(1, 2),
                new NativeTerrainSnapshotsImpl.Verification("minecraft:noise", receipt, 3L));
        try (MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class)) {
            io.when(() -> MoonriseRegionFileIO.getPriority(level, 1, 2, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenReturn(Priority.COMPLETING);
            doThrow(failure).when(policy).verifyCheckpoint(root, new SnapshotPolicy.CheckpointData(1, 2, "minecraft:noise", receipt, 3L));

            assertSame(failure, assertThrows(IOException.class,
                    () -> NativeTerrainSnapshotsImpl.finishCheckpoint(level, root, List.of(saved), policy)));
            io.verify(() -> MoonriseRegionFileIO.flushRegionStorages(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA));
        }
    }
}
