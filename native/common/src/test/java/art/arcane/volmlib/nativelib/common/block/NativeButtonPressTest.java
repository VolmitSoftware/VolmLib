package art.arcane.volmlib.nativelib.common.block;

import art.arcane.volmlib.nativelib.block.ButtonAccess;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NativeButtonPressTest {
    private final NativeButtonPress access = new NativeButtonPress();
    private CraftBlock block;
    private CraftPlayer player;
    private Switch data;
    private PluginManager plugins;
    private ButtonBlock nativeButton;
    private BlockState state;
    private ServerLevel level;
    private ServerPlayer nativePlayer;
    private BlockPos position;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        block = mock(CraftBlock.class);
        player = mock(CraftPlayer.class);
        data = mock(Switch.class);
        plugins = mock(PluginManager.class);
        Server server = mock(Server.class);
        CraftWorld world = mock(CraftWorld.class);
        nativeButton = mock(ButtonBlock.class);
        state = mock(BlockState.class);
        level = mock(ServerLevel.class);
        nativePlayer = mock(ServerPlayer.class);
        position = new BlockPos(3, 70, -8);
        when(block.getType()).thenReturn(Material.OAK_BUTTON);
        when(block.getBlockData()).thenReturn(data);
        when(block.getWorld()).thenReturn(world);
        when(player.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        when(world.getHandle()).thenReturn(level);
        when(block.getX()).thenReturn(position.getX());
        when(block.getY()).thenReturn(position.getY());
        when(block.getZ()).thenReturn(position.getZ());
        when(level.getBlockState(position)).thenReturn(state);
        when(state.getBlock()).thenReturn(nativeButton);
        when(player.getHandle()).thenReturn(nativePlayer);
    }

    @Test
    void approvedActivationCallsVanillaPressOnceWithExactNativeContext() {
        assertThat(access.press(block, player, () -> true)).isEqualTo(ButtonAccess.Result.PRESSED);

        verify(nativeButton).press(state, level, position, nativePlayer);
        verify(plugins).callEvent(any(BlockRedstoneEvent.class));
    }

    @Test
    void deniedActivationDoesNotScheduleOrMutate() {
        doAnswer(invocation -> {
            BlockRedstoneEvent event = invocation.getArgument(0);
            assertThat(event.getOldCurrent()).isZero();
            assertThat(event.getNewCurrent()).isEqualTo(15);
            event.setNewCurrent(0);
            return null;
        }).when(plugins).callEvent(any(BlockRedstoneEvent.class));

        assertThat(access.press(block, player, () -> true)).isEqualTo(ButtonAccess.Result.DENIED);

        verifyNoInteractions(nativeButton);
        verify(level, never()).getBlockState(any(BlockPos.class));
    }

    @Test
    void validationFailureBeforeActivationNeverReadsWorldState() {
        Block unowned = mock(Block.class);
        Player unownedPlayer = mock(Player.class);

        assertThat(access.press(unowned, unownedPlayer, () -> false)).isEqualTo(ButtonAccess.Result.INVALIDATED);

        verifyNoInteractions(unowned, unownedPlayer);
    }

    @Test
    void lostOwnershipAfterRedstoneEventPreventsFurtherBlockAccess() {
        AtomicBoolean valid = new AtomicBoolean(true);
        doAnswer(invocation -> {
            valid.set(false);
            clearInvocations(block);
            return null;
        }).when(plugins).callEvent(any(BlockRedstoneEvent.class));

        assertThat(access.press(block, player, valid::get)).isEqualTo(ButtonAccess.Result.INVALIDATED);

        verifyNoInteractions(block, nativeButton);
    }

    @Test
    void replacementDuringRedstoneEventNeverReceivesStalePower() {
        doAnswer(invocation -> {
            when(block.getBlockData()).thenReturn(mock(BlockData.class));
            return null;
        }).when(plugins).callEvent(any(BlockRedstoneEvent.class));

        assertThat(access.press(block, player, () -> true)).isEqualTo(ButtonAccess.Result.INVALIDATED);

        verify(level, never()).getBlockState(any(BlockPos.class));
        verifyNoInteractions(nativeButton);
    }

    @Test
    void alreadyPoweredButtonDoesNotExtendItsNativePulse() {
        when(data.isPowered()).thenReturn(true);

        assertThat(access.press(block, player, () -> true)).isEqualTo(ButtonAccess.Result.UNCHANGED);

        verifyNoInteractions(plugins, nativeButton);
    }
}
