package art.arcane.volmlib.util.update;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;

import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public final class BukkitUpdateService implements Listener, AutoCloseable {
    private static final String PROTOCOL_KEY = "volmit.update.protocol";
    private static final String PROTOCOL = "1";
    private static final String PERMISSION = "volmit.update";

    private final Plugin plugin;
    private final Options options;
    private final GitHubReleaseChecker checker;
    private final Map<String, Object> provider;
    private Map<Object, Boolean> joins = new WeakHashMap<>();
    private volatile boolean closed;
    private volatile long generation;
    private boolean enabled;

    BukkitUpdateService(Plugin plugin, Options options, GitHubReleaseChecker checker) {
        this.plugin = Objects.requireNonNull(plugin);
        this.options = Objects.requireNonNull(options);
        this.checker = Objects.requireNonNull(checker);
        provider = new ConcurrentHashMap<>(Map.of(
                PROTOCOL_KEY, PROTOCOL,
                "name", plugin.getName(),
                "installed", plugin.getDescription().getVersion(),
                "permission", options.permission(),
                "enabled", (BooleanSupplier) this::active,
                "generation", (Supplier<Long>) () -> generation,
                "check", (Function<Boolean, CompletableFuture<Map<String, String>>>) this::check));
    }

    public static BukkitUpdateService register(Plugin plugin, Options options) {
        GitHubReleaseChecker checker = new GitHubReleaseChecker(new GitHubReleaseChecker.Options(
                options.owner(), options.repository(), plugin.getDescription().getVersion(), plugin.getLogger()));
        BukkitUpdateService service = new BukkitUpdateService(plugin, options, checker);
        service.register();
        return service;
    }

    void register() {
        if (plugin.getServer().getPluginManager().getPermission(PERMISSION) == null) {
            plugin.getServer().getPluginManager().addPermission(new Permission(PERMISSION, PermissionDefault.OP));
        }
        synchronized (plugin.getServer().getServicesManager()) {
            shareJoinClaims();
            provider.put("joins", joins);
            plugin.getServer().getServicesManager().register(Map.class, provider, plugin, ServicePriority.Normal);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        reconfigure();
    }

    @SuppressWarnings("unchecked")
    private void shareJoinClaims() {
        for (Endpoint endpoint : endpoints(plugin.getServer())) {
            if (endpoint.values().get("joins") instanceof Map<?, ?> shared) {
                joins = (Map<Object, Boolean>) shared;
                return;
            }
        }
    }

    public synchronized void reconfigure() {
        boolean requested = !closed && options.enabled().getAsBoolean();
        if (enabled != requested) {
            generation++;
            enabled = requested;
            checker.setEnabled(requested);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        generation++;
        checker.close();
        HandlerList.unregisterAll(this);
        plugin.getServer().getServicesManager().unregister(Map.class, provider);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        List<Endpoint> endpoints = endpoints(plugin.getServer());
        if (closed || endpoints.isEmpty() || endpoints.get(0).values() != provider) {
            return;
        }
        synchronized (joins) {
            if (joins.put(event, Boolean.TRUE) != null) {
                return;
            }
        }
        Player player = event.getPlayer();
        List<Endpoint> permitted = endpoints.stream().filter(endpoint -> endpoint.allowed(player) && endpoint.enabled()).toList();
        if (!permitted.isEmpty()) {
            collect(permitted, false).thenAccept(results -> deliver(plugin.getServer(), player,
                    () -> announce(plugin.getServer(), player, results)));
        }
    }

    public static boolean available(Server server, CommandSender sender) {
        return endpoints(server).stream().anyMatch(endpoint -> endpoint.allowed(sender));
    }

    public static void command(Plugin owner, CommandSender sender, DirectorMiniMenu.Theme theme,
                               DirectorTextResolver resolver) {
        Server server = owner.getServer();
        List<Endpoint> permitted = endpoints(server).stream().filter(endpoint -> endpoint.allowed(sender)).toList();
        if (permitted.isEmpty()) {
            report(server, sender, List.of(), theme, resolver);
            return;
        }
        collect(permitted, true).thenAccept(results -> deliver(server, sender,
                () -> report(server, sender, results, theme, resolver)));
    }

    private boolean active() {
        return !closed && plugin.isEnabled() && options.enabled().getAsBoolean();
    }

    private CompletableFuture<Map<String, String>> check(boolean fresh) {
        if (!active()) {
            return CompletableFuture.completedFuture(Map.of("status", "DISABLED"));
        }
        return checker.checkReport(fresh).thenApply(result -> {
            GitHubReleaseChecker.Release release = result.release();
            return Map.of("status", result.status().name(),
                    "version", release == null ? "" : release.tagName(),
                    "url", release == null ? "" : release.url(),
                    "error", result.error() == null ? "" : result.error(),
                    "checked", Long.toString(result.checkedMillis()),
                    "lastSuccess", Long.toString(result.lastSuccessMillis()));
        });
    }

    private static CompletableFuture<List<Result>> collect(List<Endpoint> endpoints, boolean fresh) {
        List<CompletableFuture<Result>> requests = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            long generation = endpoint.generation();
            CompletableFuture<Map<String, String>> request;
            try {
                request = endpoint.check().apply(fresh).copy();
            } catch (RuntimeException failure) {
                request = CompletableFuture.failedFuture(failure);
            }
            requests.add(request.orTimeout(35L, TimeUnit.SECONDS)
                    .handle((value, failure) -> new Result(endpoint, generation, failure == null ? value
                            : Map.of("status", "FAILED", "error", "Provider check did not complete"))));
        }
        return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> requests.stream().map(CompletableFuture::join).toList());
    }

    private static void deliver(Server server, CommandSender sender, Runnable action) {
        List<Endpoint> remaining = endpoints(server);
        if (remaining.isEmpty()) {
            return;
        }
        Plugin owner = remaining.get(0).owner();
        if (sender instanceof Player player) {
            FoliaScheduler.runEntity(owner, player, () -> {
                if (player.isOnline()) {
                    action.run();
                }
            }, 20L);
        } else {
            FoliaScheduler.runGlobal(owner, action, 1L);
        }
    }

    private static void announce(Server server, Player player, List<Result> results) {
        List<String> names = results.stream().filter(result -> result.current(server, player)
                        && result.endpoint().enabled() && "UPDATE".equals(result.values().get("status")))
                .map(result -> result.endpoint().name()).toList();
        if (!names.isEmpty()) {
            DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.irisGreen();
            String summary = String.join(", ", names.subList(0, Math.min(3, names.size())))
                    + (names.size() > 3 ? " +" + (names.size() - 3) + " more" : "");
            ComponentText notice = ComponentText.markup("<gradient:" + theme.primaryLeft() + ":"
                    + theme.primaryRight() + "><bold>Volmit</bold></gradient>")
                    .append(ComponentText.literal(" • ").colorIfAbsent(theme.muted()))
                    .append(ComponentText.literal(names.size() + (names.size() == 1
                            ? " plugin update available" : " plugin updates available")).colorIfAbsent(theme.description()))
                    .append(ComponentText.literal("\n  " + summary).colorIfAbsent(theme.optional()))
                    .append(ComponentText.literal("  [View updates]").colorIfAbsent(theme.primaryRight()));
            ComponentMessenger.sendRunCommand(player,
                    notice, "/volmit plugins updates", ComponentText.literal("Updates available for:\n"
                            + String.join("\n", names) + "\n\nClick to view versions and release pages."));
        }
    }

    private static void report(Server server, CommandSender sender, List<Result> results,
                               DirectorMiniMenu.Theme theme, DirectorTextResolver resolver) {
        List<String> entries = new ArrayList<>();
        Map<String, List<Result>> groups = new LinkedHashMap<>();
        for (String status : List.of("UPDATE", "FAILED", "UNKNOWN", "CURRENT", "NO_RELEASE", "DISABLED")) {
            groups.put(status, new ArrayList<>());
        }
        for (Result result : results) {
            if (!result.current(server, sender)) {
                continue;
            }
            String status = result.endpoint().enabled() ? result.values().get("status") : "DISABLED";
            groups.getOrDefault(status, groups.get("FAILED")).add(result);
        }
        for (Map.Entry<String, List<Result>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) {
                continue;
            }
            if (!entries.isEmpty()) {
                entries.add(" ");
            }
            String heading = switch (group.getKey()) {
                case "UPDATE" -> "Updates available";
                case "CURRENT" -> "Up to date";
                case "NO_RELEASE" -> "No stable release";
                case "UNKNOWN" -> "Version unknown";
                case "DISABLED" -> "Checks disabled";
                default -> "Check failed";
            };
            entries.add(ComponentText.literal(heading + " (" + group.getValue().size() + ")")
                    .colorIfAbsent(theme.primaryRight()).miniMessage());
            group.getValue().sort(Comparator.comparing(result -> result.endpoint().name(), String.CASE_INSENSITIVE_ORDER));
            for (Result result : group.getValue()) {
                entries.add(reportEntry(result, group.getKey(), sender instanceof Player, theme).miniMessage());
            }
        }
        if (sender instanceof Player && !entries.isEmpty()) {
            entries.add(0, ComponentText.literal("Hover a plugin for version details.")
                    .colorIfAbsent(theme.muted()).miniMessage());
        }
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/volmit plugins updates", "/volmit plugins updates", "/volmit plugins", entries,
                "No registered update providers are available to you.", 1, Math.max(1, entries.size()));
        DirectorMiniMenu.deliverContent(sender, menu, theme, resolver);
    }

    private static ComponentText reportEntry(Result result, String status, boolean player,
                                            DirectorMiniMenu.Theme theme) {
        Map<String, String> values = result.values();
        String label = switch (status) {
            case "UPDATE" -> "update available";
            case "CURRENT" -> "current (or installed version is newer)";
            case "NO_RELEASE" -> "no stable release available";
            case "UNKNOWN" -> "versions cannot be compared";
            case "DISABLED" -> "checks disabled";
            default -> "check failed; " + values.getOrDefault("error", "unknown error");
        };
        String latest = values.getOrDefault("version", "");
        boolean failed = "FAILED".equals(status);
        String text = " " + result.endpoint().values().get("installed")
                + (latest.isEmpty() || "DISABLED".equals(status) ? "" : " -> " + latest + (failed ? " (cached)" : ""))
                + ": " + label;
        ComponentText message = ComponentText.markup("<" + theme.muted() + ">  ⇀ </" + theme.muted() + ">"
                + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">"
                + DirectorMiniMenu.escapeText(result.endpoint().name()) + "</gradient>");
        String details = "Installed: " + result.endpoint().values().get("installed")
                + (latest.isEmpty() || "DISABLED".equals(status) ? "" : "\n"
                + (failed ? "Cached release: " : "Latest release: ") + latest)
                + "\n" + label;
        message = player ? message.hover(ComponentText.literal(details))
                : message.append(ComponentText.literal(text).colorIfAbsent(theme.description()));
        String url = values.getOrDefault("url", "");
        if (!url.isEmpty() && !"DISABLED".equals(status)) {
            if (player && "UPDATE".equals(status)) {
                message = message.append(ComponentText.literal("  [View release]").colorIfAbsent(theme.primaryRight())
                        .hover(ComponentText.literal(details + "\nOpen release page"))
                        .clickOpenUrl(URI.create(url)));
            } else if (player) {
                message = message.clickOpenUrl(URI.create(url));
            } else {
                message = message.append(ComponentText.literal(" " + url));
            }
        }
        return message;
    }

    private static List<Endpoint> endpoints(Server server) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (RegisteredServiceProvider<?> registration : server.getServicesManager().getRegistrations(Map.class)) {
            if (registration.getPlugin().isEnabled() && registration.getProvider() instanceof Map<?, ?> values
                    && PROTOCOL.equals(values.get(PROTOCOL_KEY))) {
                endpoints.add(new Endpoint(registration.getPlugin(), values));
            }
        }
        endpoints.sort(Comparator.comparing(Endpoint::name, String.CASE_INSENSITIVE_ORDER));
        return endpoints;
    }

    public record Options(String owner, String repository, String permission, BooleanSupplier enabled) {
        public Options {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(repository);
            Objects.requireNonNull(permission);
            Objects.requireNonNull(enabled);
        }
    }

    private record Endpoint(Plugin owner, Map<?, ?> values) {
        String name() {
            return (String) values.get("name");
        }

        boolean allowed(CommandSender sender) {
            return sender.isOp() || sender.hasPermission(PERMISSION) || sender.hasPermission((String) values.get("permission"));
        }

        boolean enabled() {
            return ((BooleanSupplier) values.get("enabled")).getAsBoolean();
        }

        long generation() {
            return (Long) ((Supplier<?>) values.get("generation")).get();
        }

        @SuppressWarnings("unchecked")
        Function<Boolean, CompletableFuture<Map<String, String>>> check() {
            return (Function<Boolean, CompletableFuture<Map<String, String>>>) values.get("check");
        }
    }

    private record Result(Endpoint endpoint, long generation, Map<String, String> values) {
        boolean current(Server server, CommandSender sender) {
            return endpoint.allowed(sender) && endpoint.generation() == generation
                    && endpoints(server).stream().anyMatch(active -> active.values() == endpoint.values());
        }
    }
}
