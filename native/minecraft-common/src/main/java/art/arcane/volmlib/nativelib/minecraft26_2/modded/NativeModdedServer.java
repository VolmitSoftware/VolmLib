package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeRegistryDefinitions;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

public final class NativeModdedServer {
    private final MinecraftServer server;

    private NativeModdedServer(MinecraftServer server) {
        this.server = Objects.requireNonNull(server);
    }

    public static NativeModdedServer fromHandle(Object server) {
        return server == null ? null : new NativeModdedServer((MinecraftServer) server);
    }

    public static NativeModdedServer forWorld(NativeWorld world) {
        return new NativeModdedServer(((ServerLevel) world.nativeHandle()).getServer());
    }

    public static NativeRegistryDefinitions registryDefinitions(Supplier<NativeModdedServer> server) {
        return new NativeRegistryDefinitions(() -> Objects.requireNonNull(server.get(), "Minecraft server").server.registryAccess());
    }

    public RegistryAccess registryAccess() {
        return server.registryAccess();
    }

    public HolderLookup.Provider reloadableRegistries() {
        return server.reloadableRegistries().lookup();
    }

    public Thread refreshWorlds() {
        return ModdedServerLevels.refreshThread(server);
    }

    public boolean hasPlayerList() {
        return server.getPlayerList() != null;
    }

    public void forEachLiveWorld(Consumer<NativeWorld> action) {
        for (ServerLevel level : server.getAllLevels()) {
            action.accept(new ModdedPlatformWorld(level));
        }
    }

    public void execute(Runnable action) {
        server.execute(action);
    }

    public boolean isReady() {
        return server.isReady();
    }

    public <T> CompletableFuture<T> submit(Supplier<T> action) {
        return server.submit(action);
    }

    public boolean sameServer(NativeModdedServer other) {
        return other != null && server == other.server;
    }

    public boolean isServerThread() {
        return server.isSameThread();
    }

    public void halt(boolean wait) {
        server.halt(wait);
    }

    public List<NativeWorld> worlds() {
        List<ServerLevel> levels = ModdedServerLevels.levels(server);
        List<NativeWorld> worlds = new ArrayList<>(levels.size());
        for (ServerLevel level : levels) {
            worlds.add(new ModdedPlatformWorld(level));
        }
        return worlds;
    }

    public NativeWorld world(String dimension) {
        ServerLevel level = ModdedServerLevels.level(server,
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
        return level == null ? null : new ModdedPlatformWorld(level);
    }

    public NativeWorld liveWorld(String dimension) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().equals(key)) {
                return new ModdedPlatformWorld(level);
            }
        }
        return null;
    }

    public NativeWorld overworld() {
        ServerLevel level = server.overworld();
        return level == null ? null : new ModdedPlatformWorld(level);
    }

    public NativeProtocolPlayer player(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player == null ? null : NativeProtocolPlayer.fromHandle(player);
    }

    public boolean generateStructures() {
        return server.getWorldGenSettings().options().generateStructures();
    }

    public List<String> structureKeys() {
        return server.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet().stream()
                .map(Identifier::toString).toList();
    }

    public void forEachPlayer(Consumer<NativeProtocolPlayer> action) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            action.accept(NativeProtocolPlayer.fromHandle(player));
        }
    }

    public Path root() {
        return server.getWorldPath(LevelResource.ROOT);
    }

    public Path datapacks() {
        return server.getWorldPath(LevelResource.DATAPACK_DIR);
    }

    public Path dimensionFolder(String dimension) {
        return DimensionType.getStorageFolder(
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)), root());
    }

    public Collection<String> availableDatapacks() {
        return server.getPackRepository().getAvailableIds();
    }

    public Collection<String> selectedDatapacks() {
        return server.getPackRepository().getSelectedIds();
    }

    public boolean owns(NativeWorld world) {
        return ((ServerLevel) world.nativeHandle()).getServer() == server;
    }

    MinecraftServer server() {
        return server;
    }
}
