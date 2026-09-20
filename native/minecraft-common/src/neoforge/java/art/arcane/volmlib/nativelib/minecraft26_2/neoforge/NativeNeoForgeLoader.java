/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.nativelib.minecraft26_2.neoforge;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerAccess;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerLevels;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedLoader;
import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.neoforged.neoforgespi.language.IModFileInfo;

import net.neoforged.fml.ModContainer;
import java.util.Objects;
import java.io.File;
import java.nio.file.Path;

public final class NativeNeoForgeLoader implements NativeModdedLoader {
    private final NativeLoaderOptions options;
    private final PermissionNode<Boolean> blockBreakPermission;

    public NativeNeoForgeLoader(NativeLoaderOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        blockBreakPermission = new PermissionNode<>(
            options.modId(),
            options.blockBreakPermission(),
            PermissionTypes.BOOLEAN,
            (player, playerId, contexts) ->
                    player != null && Commands.LEVEL_GAMEMASTERS.check(player.permissions())
    );
    }

    public PermissionNode<Boolean> blockBreakPermission() {
        return blockBreakPermission;
    }

    @Override
    public String platformName() {
        return "neoforge";
    }

    @Override
    public String minecraftVersion() {
        return FMLLoader.getCurrent().getVersionInfo().mcVersion();
    }

    @Override
    public String modVersion() {
        return ModList.get().getModContainerById(options.modId())
                .map((ModContainer container) -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public NativeModdedServer currentServer() {
        return NativeModdedServer.fromHandle(ServerLifecycleHooks.getCurrentServer());
    }

    @Override
    public ModdedServerAccess serverAccess() {
        return new ModdedServerLevels(this::invalidateLevelCache);
    }

    private void invalidateLevelCache(MinecraftServer server) {
        server.markWorldsDirty();
    }

    @Override
    public void fireDynamicLevelLoad(NativeWorld world) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        MinecraftServer server = level.getServer();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
    }

    @Override
    public void fireDynamicLevelUnload(NativeWorld world) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        MinecraftServer server = level.getServer();
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
    }

    @Override
    public boolean clientEnvironment() {
        return FMLEnvironment.getDist().isClient();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public File modJar() {
        IModFileInfo info = ModList.get().getModFileById(options.modId());
        return info == null ? null : info.getFile().getFilePath().toFile();
    }

    @Override
    public boolean hasBlockBreakPermission(ServerPlayer player) {
        return PermissionAPI.getPermission(player, blockBreakPermission);
    }

    @Override
    public boolean canBreakBlock(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position,
            BlockState state
    ) {
        return options.breakProbe().test(() -> {
            BreakBlockEvent event = new BreakBlockEvent(level, position, state, player);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        });
    }
}
