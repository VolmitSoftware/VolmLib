package art.arcane.volmlib.nativelib.v26_3_R1.chunk;

import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import art.arcane.volmlib.nativelib.chunk.ChunkWorldContext;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NativeChunkPacketAccessTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unsupportedWorldCannotEncodeOrAdvertiseFakeMetadata() {
        NativeChunkPacketAccess packets = new NativeChunkPacketAccess();
        World world = mock(World.class);
        assertTrue(packets.context(world).isEmpty());
        assertTrue(packets.snapshot(world, new ChunkPosition(-7, 12)).isEmpty());
        verifyNoMoreInteractions(world);
    }

    @Test
    void snapshotOnlyLooksUpAlreadyLoadedColumnsAndNeverLoadsOrSendsThem() {
        NativeChunkPacketAccess packets = new NativeChunkPacketAccess();
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(world.getHandle()).thenReturn(level);
        when(level.getChunkSource()).thenReturn(chunks);
        assertTrue(packets.snapshotSupported());
        assertTrue(packets.snapshot(world, new ChunkPosition(-7, 12)).isEmpty());
        verify(chunks).getChunkNow(-7, 12);
        verifyNoMoreInteractions(chunks);
    }

    @Test
    void metadataKeepsActualWorldKeySeparateFromDimensionTypeAndUsesNormalSpawnSeed() {
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse("test:pocket"));
        ResourceKey<DimensionType> type = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse("minecraft:overworld"));
        Holder.Reference<DimensionType> holder = Holder.Reference.createStandAlone(new HolderOwner<DimensionType>() {}, type);
        when(world.getHandle()).thenReturn(level);
        when(level.dimension()).thenReturn(dimension);
        when(level.dimensionTypeRegistration()).thenReturn(holder);
        when(level.getSeed()).thenReturn(123456L);
        when(level.getMinY()).thenReturn(-64);
        when(level.getHeight()).thenReturn(384);
        when(level.getSeaLevel()).thenReturn(63);
        when(level.getGameTime()).thenReturn(17L);
        when(level.getDefaultClockTime()).thenReturn(99L);
        ChunkWorldContext context = new NativeChunkPacketAccess().context(world).orElseThrow();
        assertEquals("test:pocket", context.dimension());
        assertEquals("minecraft:overworld", context.dimensionType());
        assertEquals(BiomeManager.obfuscateSeed(123456L), context.seed());
        assertEquals(-64, context.minY());
        assertEquals(384, context.height());
        assertEquals(17L, context.gameTime());
        assertEquals(99L, context.clockTime());
    }
}
