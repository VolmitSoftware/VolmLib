package art.arcane.volmlib.util.diagnostics;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;

public record BukkitDebugSnapshot(
        Instant generatedAt,
        String pluginName,
        String pluginVersion,
        String senderType,
        ServerState server,
        List<PluginState> plugins,
        Path dataDirectory,
        Path codeSource
) {
    public BukkitDebugSnapshot {
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(senderType, "senderType");
        Objects.requireNonNull(server, "server");
        plugins = List.copyOf(plugins);
        dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
    }

    public static BukkitDebugSnapshot capture(Plugin plugin, CommandSender sender) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(sender, "sender");
        Server server = plugin.getServer();
        Plugin[] installedPlugins = server.getPluginManager().getPlugins();
        List<PluginState> plugins = new ArrayList<>(installedPlugins.length);
        for (Plugin installed : installedPlugins) {
            PluginDescriptionFile description = installed.getDescription();
            plugins.add(new PluginState(
                    installed.getName(), description.getVersion(), installed.isEnabled(),
                    description.getMain(), description.getAuthors(), description.getLoad().name(),
                    Objects.requireNonNullElse(description.getAPIVersion(), "unspecified"),
                    description.getDepend(), description.getSoftDepend()
            ));
        }
        plugins.sort(Comparator.comparing(PluginState::name, String.CASE_INSENSITIVE_ORDER));
        return new BukkitDebugSnapshot(
                Instant.now(), plugin.getName(), plugin.getDescription().getVersion(),
                sender instanceof Player ? "player" : "console-or-other", captureServer(server), plugins,
                plugin.getDataFolder().toPath(), codeSource(plugin)
        );
    }

    private static ServerState captureServer(Server server) {
        List<World> worlds = server.getWorlds();
        Map<String, Integer> environments = new TreeMap<>();
        for (World world : worlds) {
            environments.merge(world.getEnvironment().name(), 1, Integer::sum);
        }
        boolean folia = FoliaScheduler.isFoliaThreading(server);
        String bukkitVersion = server.getBukkitVersion();
        int separator = bukkitVersion.indexOf('-');
        return new ServerState(
                server.getName(), server.getVersion(), bukkitVersion,
                separator < 0 ? bukkitVersion : bukkitVersion.substring(0, separator),
                server.getOnlineMode(), server.getOnlinePlayers().size(), server.getMaxPlayers(),
                server.getViewDistance(), server.getSimulationDistance(), server.isHardcore(),
                server.getAllowFlight(), server.hasWhitelist(), server.getDefaultGameMode().name(),
                server.getSpawnRadius(), server.getIdleTimeout(), folia ? -1 : pendingTasks(server),
                worlds.size(), environments, folia ? "Folia region" : "Bukkit main-thread",
                metric(server, "getTPS"), metric(server, "getAverageTickTime")
        );
    }

    private static int pendingTasks(Server server) {
        try {
            return server.getScheduler().getPendingTasks().size();
        } catch (RuntimeException | LinkageError exception) {
            return -1;
        }
    }

    private static String metric(Server server, String methodName) {
        try {
            Method method = server.getClass().getMethod(methodName);
            Object result = method.invoke(server);
            if (result == null) {
                return "unavailable";
            }
            if (!result.getClass().isArray()) {
                return formatMetric(result);
            }
            int length = Array.getLength(result);
            List<String> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(formatMetric(Array.get(result, index)));
            }
            return String.join(", ", values);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
            return "unavailable";
        }
    }

    private static String formatMetric(Object value) {
        return value instanceof Number number
                ? String.format(Locale.ROOT, "%.2f", number.doubleValue())
                : Objects.toString(value, "unavailable");
    }

    private static Path codeSource(Plugin plugin) {
        try {
            CodeSource source = plugin.getClass().getProtectionDomain().getCodeSource();
            return source == null ? null : Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Unable to resolve the plugin code source for diagnostics", exception);
            return null;
        }
    }

    public record ServerState(
            String name,
            String version,
            String bukkitVersion,
            String minecraftVersion,
            boolean onlineMode,
            int onlinePlayers,
            int maximumPlayers,
            int viewDistance,
            int simulationDistance,
            boolean hardcore,
            boolean allowFlight,
            boolean whitelistEnabled,
            String defaultGameMode,
            int spawnRadius,
            int idleTimeout,
            int pendingSchedulerTasks,
            int loadedWorlds,
            Map<String, Integer> worldEnvironments,
            String scheduler,
            String ticksPerSecond,
            String millisecondsPerTick
    ) {
        public ServerState {
            worldEnvironments = Collections.unmodifiableMap(new TreeMap<>(worldEnvironments));
        }
    }

    public record PluginState(
            String name,
            String version,
            boolean enabled,
            String mainClass,
            List<String> authors,
            String loadOrder,
            String apiVersion,
            List<String> dependencies,
            List<String> softDependencies
    ) {
        public PluginState {
            authors = List.copyOf(authors);
            dependencies = List.copyOf(dependencies);
            softDependencies = List.copyOf(softDependencies);
        }
    }
}
