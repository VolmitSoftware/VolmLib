package art.arcane.volmlib.nativelib.chunk;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ChunkPacketSnapshotTest {
    @Test
    public void packetBytesRemainImmutableAcrossCaptureAndConsumers() {
        byte[] source = new byte[] {1, 2, 3};
        ChunkPacketSnapshot snapshot = new ChunkPacketSnapshot(new ChunkPosition(-7, 12), source);
        source[0] = 9;
        byte[] received = snapshot.payload();
        received[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, snapshot.payload());
        assertEquals(new ChunkPosition(-7, 12), snapshot.position());
    }

    @Test
    public void oversizedOrEmptyPacketsCannotEnterAChunkSnapshot() {
        ChunkPosition position = new ChunkPosition(0, 0);
        assertThrows(IllegalArgumentException.class, () -> new ChunkPacketSnapshot(position, new byte[0]));
        assertThrows(IllegalArgumentException.class,
            () -> new ChunkPacketSnapshot(position, new byte[ChunkPacketSnapshot.MAX_BYTES + 1]));
    }

    @Test
    public void worldContextPreservesResourceIdentityAndRejectsInvalidHeightOrWeather() {
        ChunkWorldContext context = context("example:pocket", 384, 1);
        assertEquals("example:pocket", context.dimension());
        assertEquals("minecraft:overworld", context.dimensionType());
        assertThrows(IllegalArgumentException.class, () -> context("example:pocket", 385, 1));
        assertThrows(IllegalArgumentException.class, () -> context("example:pocket", 384, Float.NaN));
    }

    private static ChunkWorldContext context(String dimension, int height, float rain) {
        return new ChunkWorldContext(dimension, "minecraft:overworld", 41, false, false, 63, -64, height, 99, 100, rain, 0);
    }
}
