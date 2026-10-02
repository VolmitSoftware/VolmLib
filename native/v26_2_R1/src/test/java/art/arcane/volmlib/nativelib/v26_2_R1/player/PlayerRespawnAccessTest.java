package art.arcane.volmlib.nativelib.v26_2_R1.player;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.player.PlayerRespawnAccess;
import art.arcane.volmlib.nativelib.player.RespawnPoint;
import art.arcane.volmlib.nativelib.player.RespawnPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlayerRespawnAccessTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bindingSnapshotsPersonalForcedPolicyAndSharedSpawnWithoutValidatingBlocks() {
        CraftPlayer player = mock(CraftPlayer.class);
        ServerPlayer handle = mock(ServerPlayer.class);
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel current = mock(ServerLevel.class);
        ServerLevel personal = mock(ServerLevel.class);
        ServerLevel shared = mock(ServerLevel.class);
        CraftWorld personalWorld = mock(CraftWorld.class);
        CraftWorld sharedWorld = mock(CraftWorld.class);
        LevelData.RespawnData personalData = LevelData.RespawnData.of(Level.NETHER, new BlockPos(12, 72, 24), 90, 15);
        LevelData.RespawnData sharedData = LevelData.RespawnData.of(Level.OVERWORLD, new BlockPos(100, 80, 200), 30, 5);
        when(player.getHandle()).thenReturn(handle);
        when(handle.level()).thenReturn(current);
        when(current.getServer()).thenReturn(server);
        when(handle.getRespawnConfig()).thenReturn(new ServerPlayer.RespawnConfig(personalData, true));
        when(server.findRespawnDimension()).thenReturn(shared);
        when(server.getLevel(Level.NETHER)).thenReturn(personal);
        when(personal.getWorld()).thenReturn(personalWorld);
        when(shared.getWorld()).thenReturn(sharedWorld);
        when(shared.getRespawnData()).thenReturn(sharedData);

        PlayerRespawnAccess access = NativeAdapters.require(PlayerRespawnAccess.class, "26.2");
        RespawnPolicy policy = access.snapshot(player);
        assertEquals(new RespawnPoint(personalWorld, 12, 72, 24, 90, 15), policy.personal());
        assertEquals(new RespawnPoint(sharedWorld, 100, 80, 200, 30, 5), policy.shared());
        assertTrue(policy.forced());
        when(handle.getRespawnConfig()).thenReturn(null);
        assertNull(access.snapshot(player).personal());
        assertFalse(access.snapshot(player).forced());
    }

    @Test
    void personalValidationUsesSnapshotAndNeverConsumesAnAnchorCharge() {
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        when(world.getHandle()).thenReturn(level);
        when(level.dimension()).thenReturn(Level.NETHER);
        RespawnPoint point = new RespawnPoint(world, 12, 72, 24, 90, 15);
        RespawnPolicy policy = new RespawnPolicy(point, true, point);
        Runnable charge = mock(Runnable.class);
        ServerPlayer.RespawnConfig config = new ServerPlayer.RespawnConfig(
            LevelData.RespawnData.of(Level.NETHER, new BlockPos(12, 72, 24), 90, 15), true);
        ServerPlayer.RespawnPosAngle spawn = new ServerPlayer.RespawnPosAngle(new Vec3(13.5, 72, 24.5), 45, 10,
            false, true, charge);
        try (MockedStatic<ServerPlayer> players = mockStatic(ServerPlayer.class)) {
            players.when(() -> ServerPlayer.findRespawnAndUseSpawnBlock(eq(level), any(), eq(false)))
                .thenReturn(Optional.of(spawn));
            Location location = new PlayerRespawnAccessImpl().validate(policy).orElseThrow();
            assertEquals(new Location(world, 13.5, 72, 24.5, 45, 10), location);
            players.verify(() -> ServerPlayer.findRespawnAndUseSpawnBlock(level, config, false));
        }
        verifyNoInteractions(charge);
    }

    @Test
    void invalidPersonalSpawnReturnsEmptyForTheCallerToChooseFallback() {
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        when(world.getHandle()).thenReturn(level);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        RespawnPoint point = new RespawnPoint(world, 12, 72, 24, 90, 15);
        try (MockedStatic<ServerPlayer> players = mockStatic(ServerPlayer.class)) {
            players.when(() -> ServerPlayer.findRespawnAndUseSpawnBlock(eq(level), any(), eq(false)))
                .thenReturn(Optional.empty());
            PlayerRespawnAccess access = new PlayerRespawnAccessImpl();
            assertTrue(access.validate(new RespawnPolicy(point, false, point)).isEmpty());
            assertTrue(access.validate(new RespawnPolicy(null, false, point)).isEmpty());
        }
    }

    @Test
    void sharedSpawnSearchReturnsWithoutWaitingAndUsesSnapshotCoordinates() {
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        when(world.getHandle()).thenReturn(level);
        RespawnPoint point = new RespawnPoint(world, 100, 80, 200, 30, 5);
        CompletableFuture<Vec3> pending = new CompletableFuture<>();
        try (MockedStatic<PlayerSpawnFinder> finder = mockStatic(PlayerSpawnFinder.class)) {
            finder.when(() -> PlayerSpawnFinder.findSpawn(level, new BlockPos(100, 80, 200))).thenReturn(pending);
            CompletableFuture<Location> result = new PlayerRespawnAccessImpl().findSharedSpawn(point);
            assertFalse(result.isDone());
            pending.complete(new Vec3(103.5, 81, 198.5));
            assertEquals(new Location(world, 103.5, 81, 198.5, 30, 5), result.join());
        }
    }

    @Test
    void policyCoordinatesRemainImmutableWhenReturnedLocationsAreChanged() {
        CraftWorld world = mock(CraftWorld.class);
        RespawnPoint point = new RespawnPoint(world, 12, 72, 24, 90, 15);
        Location location = point.location();
        location.setX(500);
        location.setYaw(0);
        assertEquals(new Location(world, 12, 72, 24, 90, 15), point.location());
    }
}
