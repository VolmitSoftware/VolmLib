package art.arcane.volmlib.nativelib.common.player;

import art.arcane.volmlib.nativelib.player.PlayerRespawnAccess;
import art.arcane.volmlib.nativelib.player.RespawnPoint;
import art.arcane.volmlib.nativelib.player.RespawnPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlayerRespawnAccessImpl implements PlayerRespawnAccess {
    @Override
    public RespawnPolicy snapshot(Player player) {
        ServerPlayer handle = ((CraftPlayer) Objects.requireNonNull(player, "player")).getHandle();
        MinecraftServer server = handle.level().getServer();
        ServerPlayer.RespawnConfig config = handle.getRespawnConfig();
        ServerLevel sharedLevel = server.findRespawnDimension();
        RespawnPoint shared = point(sharedLevel, sharedLevel.getRespawnData());
        if (config == null) {
            return new RespawnPolicy(null, false, shared);
        }
        ServerLevel personalLevel = server.getLevel(config.respawnData().dimension());
        RespawnPoint personal = personalLevel == null ? null : point(personalLevel, config.respawnData());
        return new RespawnPolicy(personal, config.forced(), shared);
    }

    @Override
    public Optional<Location> validate(RespawnPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        RespawnPoint personal = policy.personal();
        if (personal == null) {
            return Optional.empty();
        }
        ServerLevel level = ((CraftWorld) personal.world()).getHandle();
        LevelData.RespawnData data = LevelData.RespawnData.of(level.dimension(),
            new BlockPos(personal.x(), personal.y(), personal.z()), personal.yaw(), personal.pitch());
        ServerPlayer.RespawnConfig config = new ServerPlayer.RespawnConfig(data, policy.forced());
        return ServerPlayer.findRespawnAndUseSpawnBlock(level, config, false)
            .map(spawn -> location(personal.world(), spawn.position(), spawn.yaw(), spawn.pitch()));
    }

    @Override
    public CompletableFuture<Location> findSharedSpawn(RespawnPoint sharedSpawn) {
        Objects.requireNonNull(sharedSpawn, "sharedSpawn");
        ServerLevel level = ((CraftWorld) sharedSpawn.world()).getHandle();
        BlockPos position = new BlockPos(sharedSpawn.x(), sharedSpawn.y(), sharedSpawn.z());
        return PlayerSpawnFinder.findSpawn(level, position)
            .thenApply(spawn -> location(sharedSpawn.world(), spawn, sharedSpawn.yaw(), sharedSpawn.pitch()));
    }

    private static RespawnPoint point(ServerLevel level, LevelData.RespawnData data) {
        BlockPos position = data.pos();
        return new RespawnPoint(level.getWorld(), position.getX(), position.getY(), position.getZ(), data.yaw(), data.pitch());
    }

    private static Location location(World world, Vec3 position, float yaw, float pitch) {
        return new Location(world, position.x(), position.y(), position.z(), yaw, pitch);
    }
}
