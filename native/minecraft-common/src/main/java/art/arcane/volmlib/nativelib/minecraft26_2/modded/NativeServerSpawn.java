package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

public final class NativeServerSpawn {
    private final NativeModdedServer server;

    public NativeServerSpawn(NativeModdedServer server) {
        this.server = server;
    }

    public Respawn current() {
        LevelData.RespawnData data = server.server().getRespawnData();
        if (data == null) {
            return null;
        }
        ServerLevel level = server.server().getLevel(data.dimension());
        BlockPos position = data.pos();
        return new Respawn(level == null ? null : new ModdedPlatformWorld(level),
                new NativeBlockPoint(position.getX(), position.getY(), position.getZ()), data.yaw(), data.pitch(),
                LevelData.RespawnData.DEFAULT.equals(data));
    }

    public int surfaceAtOrigin(NativeWorld world) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        return level.getChunk(0, 0).getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) + 1;
    }

    public void update(Respawn spawn) {
        ServerLevel level = (ServerLevel) spawn.world().nativeHandle();
        NativeBlockPoint position = spawn.position();
        server.server().setRespawnData(LevelData.RespawnData.of(level.dimension(),
                new BlockPos(position.x(), position.y(), position.z()), spawn.yaw(), spawn.pitch()));
    }

    public record Respawn(NativeWorld world, NativeBlockPoint position, float yaw, float pitch, boolean initialDefault) {
    }
}
