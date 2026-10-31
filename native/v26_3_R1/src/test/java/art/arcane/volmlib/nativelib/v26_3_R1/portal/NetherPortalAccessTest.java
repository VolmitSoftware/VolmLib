package art.arcane.volmlib.nativelib.v26_3_R1.portal;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.NativeVersion;
import art.arcane.volmlib.nativelib.portal.NetherPortalAccess;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetherPortalAccessTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nativeCreationRetainsActorAxisRadiusAndVanillaRectangle() {
        CraftWorld originWorld = mock(CraftWorld.class);
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel originLevel = mock(ServerLevel.class);
        ServerLevel level = mock(ServerLevel.class);
        CraftPlayer player = mock(CraftPlayer.class);
        ServerPlayer nativePlayer = mock(ServerPlayer.class);
        PortalForcer forcer = mock(PortalForcer.class);
        when(originWorld.getHandle()).thenReturn(originLevel);
        when(world.getHandle()).thenReturn(level);
        when(player.getHandle()).thenReturn(nativePlayer);
        when(level.getPortalForcer()).thenReturn(forcer);
        when(originLevel.getBlockState(any(BlockPos.class)))
                .thenReturn(Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.Z));
        BlockPos target = new BlockPos(30, 70, 40);
        when(forcer.createPortal(target, Direction.Axis.Z, nativePlayer, 0))
                .thenReturn(Optional.of(new BlockUtil.FoundRectangle(new BlockPos(30, 65, 39), 2, 3)));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            NetherPortalAccess access = new NetherPortalAccessImpl();
            NetherPortalAccess.Rectangle rectangle = access.create(new NetherPortalAccess.Creation(player,
                    new Location(originWorld, 1, 60, 2), new Location(world, 30, 70, 40), 0)).orElseThrow();
            assertEquals(new NetherPortalAccess.Rectangle(world, 30, 65, 39, Axis.Z, 2, 3), rectangle);
        }
        verify(forcer).createPortal(target, Direction.Axis.Z, nativePlayer, 0);
    }

    @Test
    void offThreadNativePortalAccessIsRejected() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertThrows(IllegalStateException.class,
                    () -> new NetherPortalAccessImpl().find(new Location(mock(CraftWorld.class), 0, 64, 0), 128));
        }
    }

    @Test
    void capabilityResolvesForTheSupportedProvider() {
        assertTrue(NativeAdapters.available(NetherPortalAccess.class, NativeVersion.V26_3_R1));
    }

    @Test
    void movementUsesTheNativeClientMotionSnapshot() {
        CraftPlayer player = mock(CraftPlayer.class);
        ServerPlayer nativePlayer = mock(ServerPlayer.class);
        when(player.getHandle()).thenReturn(nativePlayer);
        when(nativePlayer.getKnownMovement()).thenReturn(new Vec3(0.15, -0.05, 0.3));
        when(nativePlayer.getDeltaMovement()).thenReturn(Vec3.ZERO);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertEquals(new Vector(0.15, -0.05, 0.3), new NetherPortalAccessImpl().movement(player));
        }
    }

    @Test
    void portalLookupIgnoresUnfinishedChunksAndStalePointsOfInterest() {
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        WorldBorder border = mock(WorldBorder.class);
        PoiManager points = mock(PoiManager.class);
        PoiRecord valid = mock(PoiRecord.class);
        PoiRecord unfinished = mock(PoiRecord.class);
        PoiRecord stale = mock(PoiRecord.class);
        ChunkAccess generatedChunk = mock(ChunkAccess.class);
        ChunkAccess unfinishedChunk = mock(ChunkAccess.class);
        ChunkAccess staleChunk = mock(ChunkAccess.class);
        when(world.getHandle()).thenReturn(level);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getPoiManager()).thenReturn(points);
        when(points.getInSquare(any(), any(), anyInt(), any())).thenReturn(Stream.of(valid, unfinished, stale));
        when(valid.getPos()).thenReturn(new BlockPos(0, 64, 0));
        when(unfinished.getPos()).thenReturn(new BlockPos(16, 64, 0));
        when(stale.getPos()).thenReturn(new BlockPos(32, 64, 0));
        when(level.getChunk(0, 0, ChunkStatus.EMPTY)).thenReturn(generatedChunk);
        when(level.getChunk(1, 0, ChunkStatus.EMPTY)).thenReturn(unfinishedChunk);
        when(level.getChunk(2, 0, ChunkStatus.EMPTY)).thenReturn(staleChunk);
        when(generatedChunk.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
        when(unfinishedChunk.getPersistedStatus()).thenReturn(ChunkStatus.EMPTY);
        when(staleChunk.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
        when(generatedChunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.NETHER_PORTAL.defaultBlockState());
        when(staleChunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertEquals(List.of(new Location(world, 0, 64, 0)),
                    new NetherPortalAccessImpl().find(new Location(world, 0, 64, 0), 128));
        }
    }

    @Test
    void destinationChecksStandingBoundsAndRejectsFluidsAndCollisions() {
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        CraftPlayer player = mock(CraftPlayer.class);
        ServerPlayer nativePlayer = mock(ServerPlayer.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getHandle()).thenReturn(level);
        when(player.getHandle()).thenReturn(nativePlayer);
        when(nativePlayer.getDimensions(Pose.STANDING)).thenReturn(EntityDimensions.fixed(0.6F, 1.8F));
        when(level.getMinY()).thenReturn(-64);
        when(level.getMaxY()).thenReturn(319);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(AABB.class))).thenReturn(true);
        when(level.noCollision(any(), any(AABB.class))).thenAnswer(call -> {
            AABB bounds = call.getArgument(1);
            assertEquals(1.8, bounds.maxY - bounds.minY, 0.0001);
            return true;
        });
        Location destination = new Location(world, 100.5, 65, 100.5);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            NetherPortalAccess access = new NetherPortalAccessImpl();
            assertTrue(access.clearDestination(player, destination));
            when(level.containsAnyLiquid(any(AABB.class))).thenReturn(true);
            assertFalse(access.clearDestination(player, destination));
            when(level.containsAnyLiquid(any(AABB.class))).thenReturn(false);
            when(level.noCollision(any(), any(AABB.class))).thenReturn(false);
            assertFalse(access.clearDestination(player, destination));
        }
    }
}
