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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Consumer;

public final class NativeCommandExecutor {
    private final NativeModdedServer server;
    private final Consumer<Throwable> errors;

    private NativeCommandExecutor(NativeModdedServer server, Consumer<Throwable> errors) {
        this.server = server;
        this.errors = errors;
    }

    public static NativeCommandExecutor fromWorld(NativeWorld world, Consumer<Throwable> errors) {
        if (!(world.nativeHandle() instanceof ServerLevel level) || level.getServer() == null) {
            return null;
        }
        return new NativeCommandExecutor(NativeModdedServer.fromHandle(level.getServer()), errors);
    }

    public static boolean dispatch(NativeModdedServer server, String command, Consumer<Throwable> errors) {
        if (server == null || command == null || command.isBlank()) {
            return false;
        }
        MinecraftServer nativeServer = server.server();
        String prepared = command.startsWith("/") ? command.substring(1) : command;
        try {
            nativeServer.getCommands().performPrefixedCommand(nativeServer.createCommandSourceStack(), prepared);
            return true;
        } catch (Throwable error) {
            errors.accept(error);
            return false;
        }
    }

    public boolean dispatch(String command) {
        return dispatch(server, command, errors);
    }
}
