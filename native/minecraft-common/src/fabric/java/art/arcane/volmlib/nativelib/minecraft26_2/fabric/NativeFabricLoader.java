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

package art.arcane.volmlib.nativelib.minecraft26_2.fabric;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerAccess;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerLevels;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedLoader;
import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.io.File;
import java.nio.file.Path;

public final class NativeFabricLoader implements NativeModdedLoader {
    private final NativeLoaderOptions options;
    private final Identifier blockBreakPermission;

    public NativeFabricLoader(NativeLoaderOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        blockBreakPermission = Identifier.fromNamespaceAndPath(options.modId(), options.blockBreakPermission());
    }

    @Override
    public String platformName() {
        return "fabric";
    }

    @Override
    public String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map((ModContainer container) -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(options.modId())
                .map((ModContainer container) -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public NativeModdedServer currentServer() {
        Object instance = FabricLoader.getInstance().getGameInstance();
        return instance instanceof MinecraftServer server ? NativeModdedServer.fromHandle(server) : null;
    }

    @Override
    public ModdedServerAccess serverAccess() {
        return new ModdedServerLevels(this::invalidateLevelCache);
    }

    private void invalidateLevelCache(MinecraftServer server) {
        // Intentionally empty: Fabric keeps no cached level view of its own (getAllLevels reads the live
        // map). Off-thread readers rely on the ModdedServerLevels snapshot, which the caller republishes.
    }

    @Override
    public void fireDynamicLevelLoad(NativeWorld world) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        MinecraftServer server = level.getServer();
        ServerLevelEvents.LOAD.invoker().onLevelLoad(server, level);
    }

    @Override
    public void fireDynamicLevelUnload(NativeWorld world) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        MinecraftServer server = level.getServer();
        ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, level);
    }

    @Override
    public boolean clientEnvironment() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public File modJar() {
        return FabricLoader.getInstance().getModContainer(options.modId())
                .flatMap((ModContainer container) -> container.getOrigin().getPaths().stream().findFirst())
                .map((Path p) -> p.toFile())
                .orElse(null);
    }

    @Override
    public boolean hasBlockBreakPermission(ServerPlayer player) {
        return ((PermissionContextOwner) player).checkPermission(
                blockBreakPermission,
                PermissionLevel.GAMEMASTERS
        );
    }

    @Override
    public boolean canBreakBlock(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position,
            BlockState state
    ) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(position) : null;
        return options.breakProbe().test(() -> PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, player, position, state, blockEntity));
    }
}
