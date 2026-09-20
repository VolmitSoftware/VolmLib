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

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.nio.file.Path;

public interface NativeModdedLoader {
    String platformName();

    String minecraftVersion();

    String modVersion();

    NativeModdedServer currentServer();

    ModdedServerAccess serverAccess();

    void fireDynamicLevelLoad(NativeWorld world);

    void fireDynamicLevelUnload(NativeWorld world);

    boolean clientEnvironment();

    Path configDir();

    File modJar();

    boolean hasBlockBreakPermission(ServerPlayer player);

    boolean canBreakBlock(ServerLevel level, ServerPlayer player, BlockPos position, BlockState state);
}
