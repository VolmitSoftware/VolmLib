package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeWorldTeleport {
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
        long remainingNanos = deadlineNanos == 0L ? 0L : deadlineNanos - System.nanoTime();
        long timeoutSeconds = Math.max(0L, Math.ceilDiv(remainingNanos, TimeUnit.SECONDS.toNanos(1L)));
        if (deadlineNanos != 0L) {
            if (remainingNanos <= 0L) {
                result.completeExceptionally(teleportTimeout(level, x, z, timeoutSeconds));
                return result;
            }
            CompletableFuture.delayedExecutor(remainingNanos, TimeUnit.NANOSECONDS)
                    .execute(() -> result.completeExceptionally(teleportTimeout(level, x, z, timeoutSeconds)));
        }
        runOnServer(server, () -> beginTeleport(
                result,
                player,
                server,
                level,
                x,
                y,
                z,
                deadlineNanos,
                timeoutSeconds));
        return result;
    }

    private static void beginTeleport(
            CompletableFuture<Boolean> result,
            ServerPlayer originalPlayer,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            long deadlineNanos,
            long timeoutSeconds
    ) {
        if (result.isDone()) {
            return;
        }
        if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
            result.completeExceptionally(teleportTimeout(level, x, z, timeoutSeconds));
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
        AABB footprint = originalPlayer.getDimensions(Pose.STANDING).makeBoundingBox(x, 0D, z);
        int warmRadius = warmRadius(footprint, chunkPos);
        if (footprintLoaded(level, footprint)) {
            completeTeleport(result, originalPlayer, server, level, x, y, z, blockX, blockZ, deadlineNanos, timeoutSeconds);
            return;
        }
        warmAndTeleport(result, originalPlayer, server, level, x, y, z, blockX, blockZ, chunkPos, warmRadius, deadlineNanos,
                timeoutSeconds);
    }

    private static void warmAndTeleport(
            CompletableFuture<Boolean> result,
            ServerPlayer originalPlayer,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            int blockX,
            int blockZ,
            ChunkPos chunkPos,
            int warmRadius,
            long deadlineNanos,
            long timeoutSeconds
    ) {
        AtomicBoolean ticketReleased = new AtomicBoolean();
        CompletableFuture<?> chunkLoad;
        try {
            chunkLoad = level.getChunkSource().addTicketAndLoadWithRadius(
                    TELEPORT_WARM_TICKET,
                    chunkPos,
                    warmRadius);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
            return;
        }
        if (chunkLoad == null) {
            releaseTeleportTicket(level, chunkPos, warmRadius, ticketReleased);
            result.completeExceptionally(new IllegalStateException(
                    "Chunk warm returned no completion future for " + level.dimension().identifier()
                            + " at " + chunkPos.x() + "," + chunkPos.z() + "."));
            return;
        }
        result.whenComplete((success, failure) -> runOnServer(server,
                () -> releaseTeleportTicket(level, chunkPos, warmRadius, ticketReleased)));
        chunkLoad.whenComplete((ignored, failure) -> runOnServer(server, () -> {
            releaseTeleportTicket(level, chunkPos, warmRadius, ticketReleased);
            if (result.isDone()) {
                return;
            }
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            completeTeleport(result, originalPlayer, server, level, x, y, z, blockX, blockZ, deadlineNanos, timeoutSeconds);
        }));
    }

    private static void releaseTeleportTicket(
            ServerLevel level,
            ChunkPos chunkPos,
            int warmRadius,
            AtomicBoolean ticketReleased
    ) {
        if (ticketReleased.compareAndSet(false, true)) {
            level.getChunkSource().removeTicketWithRadius(
                    TELEPORT_WARM_TICKET,
                    chunkPos,
                    warmRadius);
        }
    }

    private static void completeTeleport(
            CompletableFuture<Boolean> result,
            ServerPlayer originalPlayer,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            int blockX,
            int blockZ,
            long deadlineNanos,
            long timeoutSeconds
    ) {
        if (result.isDone()) {
            return;
        }
        if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
            result.completeExceptionally(teleportTimeout(level, x, z, timeoutSeconds));
            return;
        }
        ServerLevel active = server.getLevel(level.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(originalPlayer.getUUID());
        if (active != level || player != originalPlayer) {
            result.complete(false);
            return;
        }
        try {
            int targetY = resolveSafeTeleportY(level, player, x, z, blockX, blockZ, y);
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

    private static int resolveSafeTeleportY(ServerLevel level, ServerPlayer player, double x, double z, int blockX, int blockZ, double requestedY) {
        int initialY = requestedY == Double.MIN_VALUE
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ)
                : (int) Math.floor(requestedY);
        int startY = ModdedTeleportBounds.clampY(level.getMinY(), level.getMaxY(), initialY);
        int maximumY = ModdedTeleportBounds.maximumY(level.getMinY(), level.getMaxY());
        int minimumY = ModdedTeleportBounds.minimumY(level.getMinY(), level.getMaxY());
        for (int candidateY = startY; candidateY <= maximumY; candidateY++) {
            if (isSafeStandingPosition(level, player, x, candidateY, z)) {
                return candidateY;
            }
        }
        for (int candidateY = startY - 1; candidateY >= minimumY; candidateY--) {
            if (isSafeStandingPosition(level, player, x, candidateY, z)) {
                return candidateY;
            }
        }
        throw new IllegalStateException("No safe teleport position exists in "
                + level.dimension().identifier() + " at " + blockX + "," + blockZ + ".");
    }

    static boolean isSafeStandingPosition(ServerLevel level, ServerPlayer player, double x, int y, double z) {
        AABB body = player.getDimensions(Pose.STANDING).makeBoundingBox(x, y, z);
        if (body.minY < level.getMinY() + 1D || body.maxY > level.getMaxY()
                || !footprintLoaded(level, body) || !level.noCollision(player, body)) {
            return false;
        }
        int minimumX = ModdedTeleportBounds.blockCoordinate(body.minX);
        int maximumX = ModdedTeleportBounds.blockCoordinate(Math.nextDown(body.maxX));
        int minimumZ = ModdedTeleportBounds.blockCoordinate(body.minZ);
        int maximumZ = ModdedTeleportBounds.blockCoordinate(Math.nextDown(body.maxZ));
        for (int blockX = minimumX; blockX <= maximumX; blockX++) {
            for (int blockZ = minimumZ; blockZ <= maximumZ; blockZ++) {
                for (int blockY = y; blockY < body.maxY; blockY++) {
                    BlockState state = level.getBlockState(new BlockPos(blockX, blockY, blockZ));
                    if (!state.getFluidState().isEmpty() || StandingHazards.BLOCKS.contains(state.getBlock())) {
                        return false;
                    }
                }
            }
        }
        BlockPos support = new BlockPos(ModdedTeleportBounds.blockCoordinate(x), y - 1,
                ModdedTeleportBounds.blockCoordinate(z));
        BlockState state = level.getBlockState(support);
        if (!state.getFluidState().isEmpty() || StandingHazards.BLOCKS.contains(state.getBlock())) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, support);
        for (AABB box : shape.toAabbs()) {
            if (box.maxY > 0D && box.maxY <= 1D && x - support.getX() >= box.minX
                    && x - support.getX() <= box.maxX && z - support.getZ() >= box.minZ
                    && z - support.getZ() <= box.maxZ) {
                return true;
            }
        }
        return false;
    }

    static int warmRadius(AABB footprint, ChunkPos center) {
        int minimumX = ModdedTeleportBounds.blockCoordinate(footprint.minX) >> 4;
        int maximumX = ModdedTeleportBounds.blockCoordinate(Math.nextDown(footprint.maxX)) >> 4;
        int minimumZ = ModdedTeleportBounds.blockCoordinate(footprint.minZ) >> 4;
        int maximumZ = ModdedTeleportBounds.blockCoordinate(Math.nextDown(footprint.maxZ)) >> 4;
        return Math.max(Math.max(Math.abs(minimumX - center.x()), Math.abs(maximumX - center.x())),
                Math.max(Math.abs(minimumZ - center.z()), Math.abs(maximumZ - center.z())));
    }

    private static boolean footprintLoaded(ServerLevel level, AABB footprint) {
        int minimumX = ModdedTeleportBounds.blockCoordinate(footprint.minX) >> 4;
        int maximumX = ModdedTeleportBounds.blockCoordinate(Math.nextDown(footprint.maxX)) >> 4;
        int minimumZ = ModdedTeleportBounds.blockCoordinate(footprint.minZ) >> 4;
        int maximumZ = ModdedTeleportBounds.blockCoordinate(Math.nextDown(footprint.maxZ)) >> 4;
        for (int chunkX = minimumX; chunkX <= maximumX; chunkX++) {
            for (int chunkZ = minimumZ; chunkZ <= maximumZ; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static TimeoutException teleportTimeout(ServerLevel level, double x, double z, long timeoutSeconds) {
        return new TimeoutException("Teleport into " + level.dimension().identifier()
                + " at " + ModdedTeleportBounds.blockCoordinate(x) + ","
                + ModdedTeleportBounds.blockCoordinate(z)
                + " did not finish within " + timeoutSeconds + " seconds.");
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

    private static final class StandingHazards {
        private static final Set<Block> BLOCKS = Set.of(
                Blocks.CACTUS, Blocks.CAMPFIRE, Blocks.COBWEB, Blocks.END_GATEWAY, Blocks.END_PORTAL,
                Blocks.FIRE, Blocks.MAGMA_BLOCK, Blocks.NETHER_PORTAL, Blocks.POINTED_DRIPSTONE,
                Blocks.POWDER_SNOW, Blocks.SOUL_CAMPFIRE, Blocks.SOUL_FIRE, Blocks.SWEET_BERRY_BUSH,
                Blocks.WITHER_ROSE);
    }

}
