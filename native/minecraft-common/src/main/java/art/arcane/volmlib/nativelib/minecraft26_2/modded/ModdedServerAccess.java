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

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.concurrent.Executor;

public interface ModdedServerAccess {
    Executor levelExecutor(MinecraftServer server);

    LevelStorageSource.LevelStorageAccess levelStorage(MinecraftServer server);

    void initializeLevelData(MinecraftServer server, ServerLevel level);

    ServerLevel putLevel(MinecraftServer server, ResourceKey<Level> key, ServerLevel level);

    ServerLevel putLevelIfAbsent(MinecraftServer server, ResourceKey<Level> key, ServerLevel level);

    ServerLevel removeLevel(MinecraftServer server, ResourceKey<Level> key);

    boolean hasLevel(MinecraftServer server, ResourceKey<Level> key);
}
