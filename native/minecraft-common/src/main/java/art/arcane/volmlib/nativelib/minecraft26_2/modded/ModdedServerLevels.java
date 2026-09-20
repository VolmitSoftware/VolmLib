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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class ModdedServerLevels implements ModdedServerAccess {
    private static final int CAPTURE_ATTEMPTS = 16;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModdedServerLevels.class);
    private static final ClassValue<HostLevelPublication> HOST_LEVEL_PUBLICATIONS = new ClassValue<>() {
        @Override
        protected HostLevelPublication computeValue(Class<?> type) {
            return HostLevelPublication.detect(type, ServerLevel.class);
        }
    };
    private static final ClassValue<HostLevelDataAttachment> HOST_LEVEL_DATA_ATTACHMENTS = new ClassValue<>() {
        @Override
        protected HostLevelDataAttachment computeValue(Class<?> type) {
            return HostLevelDataAttachment.detect(type, PrimaryLevelData.class, ResourceKey.class);
        }
    };
    private static volatile Snapshot snapshot;

    private final Consumer<MinecraftServer> levelCacheInvalidator;

    public ModdedServerLevels(Consumer<MinecraftServer> levelCacheInvalidator) {
        this.levelCacheInvalidator = Objects.requireNonNull(levelCacheInvalidator);
    }

    public static Thread refreshThread(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        Thread running = server.getRunningThread();
        refreshIfStale(server);
        return running;
    }

    /**
     * Immutable view of the loaded levels. {@code server.levels} is loader-owned and published on the
     * server thread, so every off-server-thread reader must iterate this snapshot instead of
     * {@code server.getAllLevels()} to avoid ConcurrentModificationException.
     */
    public static List<ServerLevel> levels(MinecraftServer server) {
        if (server == null) {
            return List.of();
        }
        Snapshot current = snapshot;
        if (current != null && current.server() == server) {
            return current.levels();
        }
        Snapshot captured = capture(server);
        return captured == null ? List.of() : captured.levels();
    }

    /**
     * Keyed view of the loaded levels for off-server-thread lookups. Never used to gate injection:
     * {@link #hasLevel} stays on the live map so a stale snapshot cannot mask a registered level.
     */
    public static ServerLevel level(MinecraftServer server, ResourceKey<Level> key) {
        if (server == null || key == null) {
            return null;
        }
        Snapshot current = snapshot;
        Snapshot resolved = current != null && current.server() == server ? current : capture(server);
        return resolved == null ? null : resolved.byKey().get(key);
    }

    /**
     * Re-captures the snapshot when the live map no longer matches it. Server thread only; called once
     * per tick so levels registered outside {@link ModdedServerAccess} (vanilla boot, other mods) are
     * picked up without any reader touching the live map.
     */
    public static void refreshIfStale(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Snapshot current = snapshot;
        if (current == null || current.server() != server) {
            capture(server);
            return;
        }
        List<ServerLevel> cached = current.levels();
        int index = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (index >= cached.size() || cached.get(index) != level) {
                capture(server);
                return;
            }
            index++;
        }
        if (index != cached.size()) {
            capture(server);
        }
    }

    /**
     * Drops the snapshot at shutdown so a stopped server (and its level graph) is not held alive until the
     * next server publishes one. Integrated servers restart inside the same JVM.
     */
    public static void forget() {
        snapshot = null;
    }

    private static Snapshot capture(MinecraftServer server) {
        for (int attempt = 0; attempt < CAPTURE_ATTEMPTS; attempt++) {
            try {
                LinkedHashMap<ResourceKey<Level>, ServerLevel> byKey = new LinkedHashMap<>();
                for (ServerLevel level : server.getAllLevels()) {
                    byKey.put(level.dimension(), level);
                }
                Snapshot captured = new Snapshot(server, List.copyOf(byKey.values()), Map.copyOf(byKey));
                snapshot = captured;
                return captured;
            } catch (ConcurrentModificationException e) {
                Thread.onSpinWait();
            }
        }
        LOGGER.error("Could not snapshot the level map after {} attempts; readers will see the previous snapshot", CAPTURE_ATTEMPTS);
        Snapshot current = snapshot;
        return current != null && current.server() == server ? current : null;
    }

    @Override
    public Executor levelExecutor(MinecraftServer server) {
        return server.executor;
    }

    @Override
    public LevelStorageSource.LevelStorageAccess levelStorage(MinecraftServer server) {
        return server.storageSource;
    }

    @Override
    public void initializeLevelData(MinecraftServer server, ServerLevel level) {
        Object levelData = level.getLevelData();
        HostLevelDataAttachment attachment = HOST_LEVEL_DATA_ATTACHMENTS.get(levelData.getClass());
        if (!attachment.supported()) {
            return;
        }
        WorldData worldData = server.getWorldData();
        if (!(worldData instanceof PrimaryLevelData rootData)) {
            throw new IllegalStateException("Host level data attachment requires PrimaryLevelData, found "
                    + worldData.getClass().getName());
        }
        attachment.attach(levelData, rootData, level.dimension());
    }

    @Override
    public ServerLevel putLevel(MinecraftServer server, ResourceKey<Level> key, ServerLevel level) {
        HostLevelPublication hostPublication = HOST_LEVEL_PUBLICATIONS.get(server.getClass());
        if (hostPublication.supported()) {
            ServerLevel previous = server.levels.get(key);
            if (previous != level) {
                hostPublication.add(server, level);
                capture(server);
            }
            return previous;
        }
        ServerLevel previous = server.levels.put(key, level);
        if (previous != level) {
            capture(server);
            levelCacheInvalidator.accept(server);
        }
        return previous;
    }

    @Override
    public ServerLevel putLevelIfAbsent(MinecraftServer server, ResourceKey<Level> key, ServerLevel level) {
        HostLevelPublication hostPublication = HOST_LEVEL_PUBLICATIONS.get(server.getClass());
        if (hostPublication.supported()) {
            ServerLevel previous = server.levels.get(key);
            if (previous == null) {
                hostPublication.add(server, level);
                capture(server);
            }
            return previous;
        }
        ServerLevel previous = server.levels.putIfAbsent(key, level);
        if (previous == null) {
            capture(server);
            levelCacheInvalidator.accept(server);
        }
        return previous;
    }

    @Override
    public ServerLevel removeLevel(MinecraftServer server, ResourceKey<Level> key) {
        HostLevelPublication hostPublication = HOST_LEVEL_PUBLICATIONS.get(server.getClass());
        if (hostPublication.supported()) {
            ServerLevel removed = server.levels.get(key);
            if (removed != null) {
                hostPublication.remove(server, removed);
                capture(server);
            }
            return removed;
        }
        ServerLevel removed = server.levels.remove(key);
        if (removed != null) {
            capture(server);
            levelCacheInvalidator.accept(server);
        }
        return removed;
    }

    @Override
    public boolean hasLevel(MinecraftServer server, ResourceKey<Level> key) {
        return server.levels.containsKey(key);
    }

    private record Snapshot(MinecraftServer server, List<ServerLevel> levels,
                            Map<ResourceKey<Level>, ServerLevel> byKey) {
    }

    static final class HostLevelPublication {
        private final Method addLevel;
        private final Method removeLevel;

        private HostLevelPublication(Method addLevel, Method removeLevel) {
            this.addLevel = addLevel;
            this.removeLevel = removeLevel;
        }

        static HostLevelPublication detect(Class<?> serverType, Class<?> levelType) {
            try {
                Method addLevel = serverType.getMethod("addLevel", levelType);
                Method removeLevel = serverType.getMethod("removeLevel", levelType);
                return new HostLevelPublication(addLevel, removeLevel);
            } catch (NoSuchMethodException e) {
                return new HostLevelPublication(null, null);
            }
        }

        boolean supported() {
            return addLevel != null && removeLevel != null;
        }

        void add(Object server, Object level) {
            invoke(addLevel, server, level);
        }

        void remove(Object server, Object level) {
            invoke(removeLevel, server, level);
        }

        private void invoke(Method method, Object server, Object level) {
            try {
                method.invoke(server, level);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access host level publication method "
                        + method.getName(), e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new IllegalStateException("Host level publication method " + method.getName()
                        + " failed", cause);
            }
        }
    }

    static final class HostLevelDataAttachment {
        private final Method attach;

        private HostLevelDataAttachment(Method attach) {
            this.attach = attach;
        }

        static HostLevelDataAttachment detect(Class<?> levelDataType, Class<?> rootDataType,
                                              Class<?> dimensionKeyType) {
            try {
                return new HostLevelDataAttachment(levelDataType.getMethod(
                        "attach", rootDataType, dimensionKeyType));
            } catch (NoSuchMethodException e) {
                return new HostLevelDataAttachment(null);
            }
        }

        boolean supported() {
            return attach != null;
        }

        void attach(Object levelData, Object rootData, Object dimensionKey) {
            try {
                attach.invoke(levelData, rootData, dimensionKey);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access host level data attachment method", e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new IllegalStateException("Host level data attachment failed", cause);
            }
        }
    }
}
