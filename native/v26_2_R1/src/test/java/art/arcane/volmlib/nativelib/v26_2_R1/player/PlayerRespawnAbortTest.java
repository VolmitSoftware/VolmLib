package art.arcane.volmlib.nativelib.v26_2_R1.player;

import art.arcane.volmlib.nativelib.player.PlayerRespawnAccess;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import io.papermc.paper.configuration.GlobalConfiguration;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerRespawnAbortTest {
    private static MockedStatic<GlobalConfiguration> configuration;

    @BeforeAll
    static void bootstrap() {
        GlobalConfiguration defaults = new GlobalConfiguration();
        defaults.misc = defaults.new Misc();
        defaults.packetLimiter = defaults.new PacketLimiter();
        configuration = mockStatic(GlobalConfiguration.class);
        configuration.when(GlobalConfiguration::get).thenReturn(defaults);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterAll
    static void closeConfiguration() {
        configuration.close();
    }

    @Test
    void delayedKickTransportClosesBeforeTheNativeRespawnGuardRuns() throws ReflectiveOperationException {
        EmbeddedChannel channel = new EmbeddedChannel();
        Fixture fixture = new Fixture(channel);
        try (MockedStatic<Bukkit> bukkit = owner()) {
            assertTrue(fixture.connection.isConnected());
            fixture.access.abortRespawn(fixture.player, "No safe respawn location");
            verify(fixture.player).kickPlayer("No safe respawn location");
            assertFalse(fixture.connection.isConnected());
            assertTrue(fixture.listener.isDisconnected());
            verify(fixture.handle, never()).setHealth(anyFloat());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void incompleteTransportClosureCannotReportASuccessfulAbort() throws ReflectiveOperationException {
        Channel channel = mock(Channel.class);
        ChannelFuture closed = mock(ChannelFuture.class);
        when(channel.isOpen()).thenReturn(true);
        when(channel.close()).thenReturn(closed);
        when(closed.awaitUninterruptibly()).thenReturn(closed);
        Fixture fixture = new Fixture(channel);
        try (MockedStatic<Bukkit> bukkit = owner()) {
            assertThrows(IllegalStateException.class,
                    () -> fixture.access.abortRespawn(fixture.player, "No safe respawn location"));
        }
    }

    @Test
    void foreignThreadsCannotAbortARespawn() throws ReflectiveOperationException {
        EmbeddedChannel channel = new EmbeddedChannel();
        Fixture fixture = new Fixture(channel);
        try (MockedStatic<Bukkit> bukkit = owner()) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class,
                    () -> fixture.access.abortRespawn(fixture.player, "No safe respawn location"));
            verify(fixture.player, never()).kickPlayer(any());
            assertTrue(fixture.connection.isConnected());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void foliaCannotUseThePaperRespawnAbort() throws ReflectiveOperationException {
        EmbeddedChannel channel = new EmbeddedChannel();
        Fixture fixture = new Fixture(channel);
        try (MockedStatic<Bukkit> bukkit = owner(); MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isFoliaThreading(any())).thenReturn(true);
            assertThrows(IllegalStateException.class,
                    () -> fixture.access.abortRespawn(fixture.player, "No safe respawn location"));
            verify(fixture.player, never()).kickPlayer(any());
            assertTrue(fixture.connection.isConnected());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private MockedStatic<Bukkit> owner() {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getName).thenReturn("Paper");
        return bukkit;
    }

    private static final class Fixture {
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final ServerPlayer handle = mock(ServerPlayer.class);
        private final ServerGamePacketListenerImpl listener = mock(ServerGamePacketListenerImpl.class);
        private final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        private final PlayerRespawnAccess access = new PlayerRespawnAccessImpl();

        private Fixture(Channel channel) throws ReflectiveOperationException {
            connection.channel = channel;
            Field transport = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
            transport.setAccessible(true);
            transport.set(listener, connection);
            listener.player = handle;
            handle.connection = listener;
            when(player.getHandle()).thenReturn(handle);
            when(listener.isDisconnected()).thenCallRealMethod();
        }
    }
}
