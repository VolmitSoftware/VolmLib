package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeWorldTeleport {
    private static final int TELEPORT_WARM_RADIUS = 0;
    private static final long TELEPORT_TIMEOUT_SECONDS = 10L;
    private static final TicketType TELEPORT_WARM_TICKET = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    private NativeWorldTeleport() {
    }

    public static CompletableFuture<Boolean> teleport(NativeProtocolPlayer player, Destination destination) {
        return teleportAsync(player == null ? null : player.player(), destination.server().server(),
                destination.world() == null ? null : (ServerLevel) destination.world().nativeHandle(),
                destination.x(), destination.y(), destination.z(), destination.deadlineNanos());
    }

    public record Destination(NativeModdedServer server, NativeWorld world, double x, double y, double z,
                              long deadlineNanos) {
    }

    private static CompletableFuture<Boolean> teleportAsync(
            ServerPlayer player,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            long deadlineNanos
    ) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (player == null || server == null || level == null || level.getServer() != server) {
            result.complete(false);
            return result;
        }
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || (y != Double.MIN_VALUE && !Double.isFinite(y))) {
            result.completeExceptionally(new IllegalArgumentException("Teleport coordinates must be finite."));
            return result;
        }
        if (deadlineNanos != 0L) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                result.completeExceptionally(teleportTimeout(level, x, z));
                return result;
            }
            result.orTimeout(remainingNanos, TimeUnit.NANOSECONDS);
        }
        UUID playerId = player.getUUID();
        runOnServer(server, () -> beginTeleport(
                result,
                playerId,
                server,
                level,
                x,
                y,
                z,
                deadlineNanos));
        return result;
    }

    private static void beginTeleport(
            CompletableFuture<Boolean> result,
            UUID playerId,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            long deadlineNanos
    ) {
        if (result.isDone()) {
            return;
        }
        if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
            result.completeExceptionally(teleportTimeout(level, x, z));
            return;
        }
        ServerLevel active = server.getLevel(level.dimension());
        if (active != level) {
            result.complete(false);
            return;
        }
        int blockX = ModdedTeleportBounds.blockCoordinate(x);
        int blockZ = ModdedTeleportBounds.blockCoordinate(z);
        ChunkPos chunkPos = new ChunkPos(blockX >> 4, blockZ >> 4);
        if (level.getChunkSource().hasChunk(chunkPos.x(), chunkPos.z())) {
            completeTeleport(result, playerId, server, level, x, y, z, blockX, blockZ, deadlineNanos);
            return;
        }
        warmAndTeleport(result, playerId, server, level, x, y, z, blockX, blockZ, chunkPos, deadlineNanos);
    }

    private static void warmAndTeleport(
            CompletableFuture<Boolean> result,
            UUID playerId,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            int blockX,
            int blockZ,
            ChunkPos chunkPos,
            long deadlineNanos
    ) {
        AtomicBoolean ticketReleased = new AtomicBoolean();
        CompletableFuture<?> chunkLoad;
        try {
            chunkLoad = level.getChunkSource().addTicketAndLoadWithRadius(
                    TELEPORT_WARM_TICKET,
                    chunkPos,
                    TELEPORT_WARM_RADIUS);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
            return;
        }
        if (chunkLoad == null) {
            releaseTeleportTicket(level, chunkPos, ticketReleased);
            result.completeExceptionally(new IllegalStateException(
                    "Chunk warm returned no completion future for " + level.dimension().identifier()
                            + " at " + chunkPos.x() + "," + chunkPos.z() + "."));
            return;
        }
        result.whenComplete((success, failure) -> runOnServer(server,
                () -> releaseTeleportTicket(level, chunkPos, ticketReleased)));
        chunkLoad.whenComplete((ignored, failure) -> runOnServer(server, () -> {
            releaseTeleportTicket(level, chunkPos, ticketReleased);
            if (result.isDone()) {
                return;
            }
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            completeTeleport(result, playerId, server, level, x, y, z, blockX, blockZ, deadlineNanos);
        }));
    }

    private static void releaseTeleportTicket(
            ServerLevel level,
            ChunkPos chunkPos,
            AtomicBoolean ticketReleased
    ) {
        if (ticketReleased.compareAndSet(false, true)) {
            level.getChunkSource().removeTicketWithRadius(
                    TELEPORT_WARM_TICKET,
                    chunkPos,
                    TELEPORT_WARM_RADIUS);
        }
    }

    private static void completeTeleport(
            CompletableFuture<Boolean> result,
            UUID playerId,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            int blockX,
            int blockZ,
            long deadlineNanos
    ) {
        if (result.isDone()) {
            return;
        }
        if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
            result.completeExceptionally(teleportTimeout(level, x, z));
            return;
        }
        ServerLevel active = server.getLevel(level.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (active != level || player == null) {
            result.complete(false);
            return;
        }
        try {
            int targetY = resolveSafeTeleportY(level, blockX, blockZ, y);
            boolean teleported = player.teleportTo(
                    level,
                    x,
                    targetY,
                    z,
                    Set.<Relative>of(),
                    player.getYRot(),
                    player.getXRot(),
                    false);
            result.complete(teleported);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
    }

    private static int resolveSafeTeleportY(ServerLevel level, int blockX, int blockZ, double requestedY) {
        int initialY = requestedY == Double.MIN_VALUE
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ)
                : (int) Math.floor(requestedY);
        int startY = ModdedTeleportBounds.clampY(level.getMinY(), level.getMaxY(), initialY);
        int maximumY = ModdedTeleportBounds.maximumY(level.getMinY(), level.getMaxY());
        int minimumY = ModdedTeleportBounds.minimumY(level.getMinY(), level.getMaxY());
        for (int candidateY = startY; candidateY <= maximumY; candidateY++) {
            if (isSafeStandingPosition(level, blockX, candidateY, blockZ)) {
                return candidateY;
            }
        }
        for (int candidateY = startY - 1; candidateY >= minimumY; candidateY--) {
            if (isSafeStandingPosition(level, blockX, candidateY, blockZ)) {
                return candidateY;
            }
        }
        throw new IllegalStateException("No safe teleport position exists in "
                + level.dimension().identifier() + " at " + blockX + "," + blockZ + ".");
    }

    private static boolean isSafeStandingPosition(ServerLevel level, int blockX, int blockY, int blockZ) {
        BlockPos feet = new BlockPos(blockX, blockY, blockZ);
        BlockPos head = feet.above();
        BlockPos support = feet.below();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet).getFluidState().isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getBlockState(head).getFluidState().isEmpty()
                && !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
    }

    private static TimeoutException teleportTimeout(ServerLevel level, double x, double z) {
        return new TimeoutException("Teleport into " + level.dimension().identifier()
                + " at " + ModdedTeleportBounds.blockCoordinate(x) + ","
                + ModdedTeleportBounds.blockCoordinate(z)
                + " exceeded " + TELEPORT_TIMEOUT_SECONDS + " seconds.");
    }

    private static void runOnServer(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
            return;
        }
        server.execute(task);
    }

    public static int evacuate(NativeModdedServer host, NativeWorld world) {
        MinecraftServer server = host.server();
        ServerLevel from = (ServerLevel) world.nativeHandle();
        ServerLevel fallback = server.overworld();
        if (fallback == from) {
            return 0;
        }
        BlockPos spawn = fallback.getRespawnData().pos();
        int spawnY = fallback.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        List<ServerPlayer> players = new ArrayList<>(from.players());
        for (ServerPlayer player : players) {
            player.teleportTo(fallback, spawn.getX() + 0.5D, spawnY, spawn.getZ() + 0.5D, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        }
        return players.size();
    }

}
