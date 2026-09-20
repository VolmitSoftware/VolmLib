package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.view.WorldMarker;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.view.WorldView;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class NativeWorldView implements WorldView {
    private final NativeModdedServer host;
    private final NativeWorld world;
    private final MinecraftServer server;
    private final ServerLevel level;

    public NativeWorldView(NativeModdedServer host, NativeWorld world) {
        this.host = Objects.requireNonNull(host, "host");
        this.world = Objects.requireNonNull(world, "world");
        server = host.server();
        level = (ServerLevel) world.nativeHandle();
    }

    @Override
    public List<WorldMarker> players() {
        return server.isSameThread() ? playerMarkers() : server.submit(this::playerMarkers).join();
    }

    @Override
    public void requestEntities(Consumer<List<WorldMarker>> sink) {
        server.execute(() -> {
            List<WorldMarker> markers = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ServerPlayer || !(entity instanceof LivingEntity living)) {
                    continue;
                }
                Vec3 position = living.position();
                markers.add(WorldMarker.entity(living.getType().toShortString(), position.x(), position.y(), position.z(),
                        living.getHealth(), living.getMaxHealth()));
            }
            sink.accept(markers);
        });
    }

    @Override
    public void execute(Runnable task) {
        server.execute(task);
    }

    @Override
    public Optional<TeleportOperation> teleport(UUID playerId, Destination destination) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("World view teleports must run on the server thread");
        }
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            if (playerId != null || level.players().isEmpty()) {
                return Optional.empty();
            }
            player = level.players().getFirst();
        }
        CompletableFuture<Boolean> result = NativeWorldTeleport.teleport(NativeProtocolPlayer.fromHandle(player),
                new NativeWorldTeleport.Destination(host, world, destination.x(), destination.y(),
                        destination.z(), destination.deadlineNanos()));
        return Optional.of(new TeleportOperation(player.getUUID(), result));
    }

    private List<WorldMarker> playerMarkers() {
        List<WorldMarker> markers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            Vec3 position = player.position();
            markers.add(WorldMarker.player(player.getScoreboardName(), position.x(), position.z()));
        }
        return markers;
    }

}
