package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.UUID;

public final class NativeCommandBossBar {
    private final MinecraftServer server;
    private final UUID viewer;
    private final ServerBossEvent bar;

    public NativeCommandBossBar(NativeCommandSource source, Options options) {
        server = source.source().getServer();
        viewer = source.playerId();
        bar = new ServerBossEvent(options.id(), options.title().component(),
                BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(0.0F);
        attachViewer();
    }

    public void update(NativeCommandText title, float progress, boolean paused) {
        attachViewer();
        bar.setProgress(progress);
        bar.setColor(paused ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
        bar.setName(title.component());
    }

    public void close() {
        bar.removeAllPlayers();
        bar.setVisible(false);
    }

    private void attachViewer() {
        if (viewer == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(viewer);
        if (player != null && !bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    public record Options(UUID id, NativeCommandText title) {
    }
}
