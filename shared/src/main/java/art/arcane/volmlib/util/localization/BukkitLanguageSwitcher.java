package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class BukkitLanguageSwitcher implements AutoCloseable, Listener {
    private static final String PROTOCOL_KEY = "volmit.language.protocol";
    private static final String PROTOCOL = "2";
    private static final int PAGE_SIZE = 9;

    private final Plugin plugin;
    private final PluginLanguageService languages;
    private final Options options;
    private final Map<String, Object> provider;
    private final BukkitVolmitCommand commandRegistration;
    private final BukkitLanguageEditor editor;
    private volatile boolean closed;

    private BukkitLanguageSwitcher(Plugin plugin, PluginLanguageService languages, Options options) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.options = Objects.requireNonNull(options, "options");
        commandRegistration = new BukkitVolmitCommand(plugin, this);
        editor = new BukkitLanguageEditor(plugin, new BukkitLanguageEditor.Options(languages, options,
                player -> command(player, new String[]{"server"})));
        provider = createProvider();
    }

    public static BukkitLanguageSwitcher register(Plugin plugin, PluginLanguageService languages, Options options) {
        BukkitLanguageSwitcher switcher = new BukkitLanguageSwitcher(plugin, languages, options);
        if (plugin.getServer().getPluginManager().getPermission("volmit.language.self") == null) {
            plugin.getServer().getPluginManager().addPermission(new Permission("volmit.language.self", PermissionDefault.TRUE));
        }
        String selfPermission = selfPermission(plugin.getName());
        if (plugin.getServer().getPluginManager().getPermission(selfPermission) == null) {
            plugin.getServer().getPluginManager().addPermission(new Permission(selfPermission, PermissionDefault.TRUE));
        }
        if (plugin.getServer().getPluginManager().getPermission("volmit.language.admin") == null) {
            plugin.getServer().getPluginManager().addPermission(new Permission("volmit.language.admin", PermissionDefault.OP));
        }
        plugin.getServer().getServicesManager().register(Map.class, switcher.provider, plugin, ServicePriority.Normal);
        plugin.getServer().getPluginManager().registerEvents(switcher, plugin);
        plugin.getServer().getPluginManager().registerEvents(switcher.editor, plugin);
        switcher.commandRegistration.claim();
        return switcher;
    }

    public void open(CommandSender sender) {
        command(sender, new String[0]);
    }

    public void openEditor(Player player) {
        editor.open(player, null);
    }

    public void openEditor(Player player, Consumer<Player> back) {
        editor.open(player, null, back);
    }

    public boolean command(CommandSender sender, String[] arguments) {
        return LanguageAudience.call(sender instanceof Player player ? player.getUniqueId() : null,
                () -> execute(sender, arguments));
    }

    public List<String> complete(CommandSender sender, String[] arguments) {
        if (closed || arguments.length == 0) {
            return List.of();
        }
        List<Endpoint> selected = List.of(endpoint(provider));
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (arguments.length == 1) {
            if (canSelectSelf(sender, selected)) {
                candidates.add("self");
            }
            if (canSelectServer(sender, selected)) {
                candidates.add("server");
            }
        } else if (arguments.length == 2) {
            if (arguments[0].equalsIgnoreCase("self") && canSelectSelf(sender, selected)) {
                candidates.addAll(commonLocales(selected));
                candidates.add("reset");
            } else if (arguments[0].equalsIgnoreCase("server") && canSelectServer(sender, selected)) {
                candidates.addAll(commonLocales(selected));
                if (sender instanceof Player) {
                    candidates.add("edit");
                }
            }
        } else if (arguments.length == 3 && sender instanceof Player
                && arguments[0].equalsIgnoreCase("server") && arguments[1].equalsIgnoreCase("edit")
                && canSelectServer(sender, selected)) {
            candidates.addAll(commonLocales(selected));
        }
        return matching(candidates, arguments[arguments.length - 1]);
    }

    boolean commandVolmit(CommandSender sender, String[] arguments) {
        return LanguageAudience.call(sender instanceof Player player ? player.getUniqueId() : null, () -> {
            if (closed) {
                return true;
            }
            if (arguments.length == 0) {
                showVolmitHome(sender);
                return true;
            }
            List<Endpoint> selected = endpoints();
            if (arguments.length == 1 && arguments[0].equalsIgnoreCase("plugins")) {
                showPluginTools(sender, selected);
                return true;
            }
            if (arguments.length < 2 || !arguments[0].equalsIgnoreCase("plugins")) {
                message(sender, "Usage: /volmit plugins [languages|debug]");
                return true;
            }
            if (arguments[1].equalsIgnoreCase("languages")) {
                if (selected.isEmpty()) {
                    message(sender, "No Volmit language providers are available.");
                    return true;
                }
                if (arguments.length > 3 || !allowed(sender, "server", selected)) {
                    if (arguments.length > 3) {
                        message(sender, "Usage: /volmit plugins languages [locale]");
                    }
                    return true;
                }
                Selection selection = new Selection(
                        "server", selected, "/volmit plugins languages", "all Volmit plugins", false);
                executeSelection(sender, selection, arguments.length == 3 ? arguments[2] : null);
                return true;
            }
            if (arguments[1].equalsIgnoreCase("debug")) {
                executeDebug(sender, arguments);
                return true;
            }
            message(sender, "Usage: /volmit plugins [languages|debug]");
            return true;
        });
    }

    List<String> completeVolmit(CommandSender sender, String[] arguments) {
        List<Endpoint> selected = endpoints();
        if (closed || arguments.length == 0) {
            return List.of();
        }
        if (arguments.length == 1) {
            return matching(List.of("plugins"), arguments[0]);
        }
        if (!arguments[0].equalsIgnoreCase("plugins")) {
            return List.of();
        }
        if (arguments.length == 2) {
            ArrayList<String> tools = new ArrayList<>();
            if (canSelectServer(sender, selected)) {
                tools.add("languages");
            }
            if (!allowedDebugEndpoints(sender).isEmpty()) {
                tools.add("debug");
            }
            return matching(tools, arguments[1]);
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("languages")
                && canSelectServer(sender, selected)) {
            return matching(commonLocales(selected), arguments[2]);
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("debug")) {
            ArrayList<String> providers = new ArrayList<>();
            List<DebugEndpoint> allowed = allowedDebugEndpoints(sender);
            if (!allowed.isEmpty()) {
                providers.add("all");
            }
            for (DebugEndpoint endpoint : allowed) {
                providers.add(endpoint.name());
            }
            return matching(providers, arguments[2]);
        }
        return arguments.length == 4 && arguments[1].equalsIgnoreCase("debug")
                ? matching(List.of("upload=true", "upload=false"), arguments[3]) : List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        languages.snapshot(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        editor.close();
        HandlerList.unregisterAll(this);
        plugin.getServer().getServicesManager().unregister(Map.class, provider);
        commandRegistration.release();
        for (Map<?, ?> remaining : providers()) {
            ((Runnable) remaining.get("command.claim")).run();
        }
    }

    private Map<String, Object> createProvider() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        values.put(PROTOCOL_KEY, PROTOCOL);
        values.put("name", plugin.getName());
        values.put("permission", options.adminPermission());
        values.put("locales", (Supplier<List<String>>) languages::availableLocales);
        values.put("default", (Supplier<String>) languages::defaultLocale);
        values.put("selected", (Function<UUID, String>) languages::effectiveLocale);
        values.put("self", (BiFunction<UUID, String, CompletableFuture<String>>) this::selectPlayer);
        values.put("server", (Function<String, CompletableFuture<String>>) locale ->
                languages.selectDefault(locale).thenApply(ignored -> languages.defaultLocale()));
        values.put("command.claim", (Runnable) commandRegistration::claim);
        return values;
    }

    private CompletableFuture<String> selectPlayer(UUID playerId, String locale) {
        return locale.equalsIgnoreCase("reset")
                ? languages.clearPlayer(playerId).thenApply(ignored -> "reset")
                : languages.selectPlayer(playerId, locale).thenApply(ignored -> languages.effectiveLocale(playerId));
    }

    private boolean execute(CommandSender sender, String[] arguments) {
        if (closed) {
            message(sender, "Language selection is unavailable while the plugin is stopping.");
            return true;
        }
        List<Endpoint> selected = List.of(endpoint(provider));
        if (arguments.length == 0) {
            showLanguageHome(sender, selected);
            return true;
        }
        if (arguments.length >= 2 && arguments[0].equalsIgnoreCase("server")
                && arguments[1].equalsIgnoreCase("edit")) {
            if (!allowed(sender, "server", selected)) {
                return true;
            }
            if (!(sender instanceof Player player)) {
                message(sender, "Open the language editor in game.");
            } else if (arguments.length > 3) {
                message(sender, "Usage: /" + options.command() + " language server edit [locale]");
            } else {
                editor.open(player, arguments.length == 3 ? arguments[2] : null);
            }
            return true;
        }
        String scope = arguments[0].toLowerCase(Locale.ROOT);
        if ((!scope.equals("self") && !scope.equals("server")) || arguments.length > 2) {
            message(sender, "Usage: /" + options.command() + " language self [locale|reset] or server [locale]");
            return true;
        }
        if (!allowed(sender, scope, selected)) {
            return true;
        }
        Selection selection = new Selection(scope, selected, "/" + options.command() + " language " + scope,
                plugin.getName(), true);
        executeSelection(sender, selection, arguments.length == 2 ? arguments[1] : null);
        return true;
    }

    private void executeSelection(CommandSender sender, Selection selection, String value) {
        if (value == null) {
            showLocales(sender, selection, 1);
        } else if (value.startsWith("page=")) {
            try {
                showLocales(sender, selection, Integer.parseInt(value.substring(5)));
            } catch (NumberFormatException exception) {
                message(sender, "Use a numeric language page.");
            }
        } else {
            select(sender, selection, value);
        }
    }

    private boolean allowed(CommandSender sender, String scope, List<Endpoint> endpoints) {
        if (scope.equals("self")) {
            if (!(sender instanceof Player)) {
                message(sender, "Player language preferences must be selected in game.");
                return false;
            }
            if (!sender.hasPermission("volmit.language.self")) {
                message(sender, "You do not have permission to select your language.");
                return false;
            }
            for (Endpoint endpoint : endpoints) {
                if (!sender.hasPermission(selfPermission(endpoint.name()))) {
                    message(sender, "You do not have permission to select your language for " + endpoint.name() + ".");
                    return false;
                }
            }
            return true;
        }
        for (Endpoint endpoint : endpoints) {
            if (!sender.hasPermission("volmit.language.admin") && !sender.hasPermission(endpoint.permission())) {
                message(sender, "You do not have permission to change the server language for " + endpoint.name() + ".");
                return false;
            }
        }
        return true;
    }

    private static String selfPermission(String pluginName) {
        return pluginName.toLowerCase(Locale.ROOT) + ".language.self";
    }

    private boolean canSelectSelf(CommandSender sender, List<Endpoint> endpoints) {
        return sender instanceof Player && sender.hasPermission("volmit.language.self") && !endpoints.isEmpty()
                && endpoints.stream().allMatch(endpoint -> sender.hasPermission(selfPermission(endpoint.name())));
    }

    private boolean canSelectServer(CommandSender sender, List<Endpoint> endpoints) {
        return !endpoints.isEmpty() && endpoints.stream().allMatch(endpoint ->
                sender.hasPermission("volmit.language.admin") || sender.hasPermission(endpoint.permission()));
    }

    private void showLocales(CommandSender sender, Selection selection, int requestedPage) {
        List<String> locales = commonLocales(selection.endpoints());
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(locales.size(), requestedPage, PAGE_SIZE);
        String base = selection.command();
        DirectorMiniMenu.Theme theme = options.theme();
        List<String> lines = new ArrayList<>(PAGE_SIZE + 6);
        lines.add(selection.pluginOnly()
                ? DirectorMiniMenu.banner(base, theme)
                : DirectorMiniMenu.banner(base, page, theme));
        lines.add(DirectorMiniMenu.backLink(selection.pluginOnly()
                ? "/" + options.command() + " language" : "/volmit plugins", theme, options.textResolver()));
        String current = selectedLocale(sender, selection.scope(), selection.endpoints());
        if (!selection.pluginOnly()) {
            lines.add(styled(localized(BukkitLanguageMessages.CURRENT,
                    MessageArgs.builder().untrusted("locale", current).build()), theme.description()));
        }
        if (selection.pluginOnly() && page.page() == 1) {
            String pluginBase = "/" + options.command() + " language";
            if (canSelectSelf(sender, selection.endpoints())) {
                lines.add(link(sender, localized(BukkitLanguageMessages.YOUR_LANGUAGE), pluginBase + " self",
                        localized(BukkitLanguageMessages.YOUR_DESCRIPTION)));
            }
            if (canSelectServer(sender, selection.endpoints())) {
                lines.add(link(sender, localized(BukkitLanguageMessages.SERVER_DEFAULT), pluginBase + " server",
                        localized(BukkitLanguageMessages.SERVER_DESCRIPTION)));
            }
        }
        for (int index = page.startIndex(); index < page.endIndex(); index++) {
            String locale = locales.get(index);
            String name = VolmitLocales.displayName(locale).orElse(locale);
            lines.add(languageLink(sender, locale, name, locale.equalsIgnoreCase(current),
                    base + " " + locale));
        }
        if (selection.pluginOnly() && page.page() == 1 && selection.scope().equals("self")
                && sender instanceof Player player && languages.playerLocale(player.getUniqueId()).isPresent()) {
            lines.add(link(sender, localized(BukkitLanguageMessages.USE_SERVER_DEFAULT), base + " reset",
                    localized(BukkitLanguageMessages.REMOVE_PERSONAL_DESCRIPTION)));
        }
        lines.add(DirectorMiniMenu.paginationBar(page, base, theme, options.textResolver()));
        DirectorMiniMenu.deliver(sender, lines);
    }

    private void showLanguageHome(CommandSender sender, List<Endpoint> endpoints) {
        String command = "/" + options.command() + " language";
        String serverLocale = selectedLocale(sender, "server", endpoints);
        ArrayList<String> entries = new ArrayList<>();
        if (canSelectSelf(sender, endpoints) && sender instanceof Player) {
            String personalLocale = selectedLocale(sender, "self", endpoints);
            entries.add(link(sender, localized(BukkitLanguageMessages.YOUR_LANGUAGE), command + " self",
                    localized(BukkitLanguageMessages.CURRENT,
                            MessageArgs.builder().untrusted("locale", personalLocale).build())));
            if (canSelectServer(sender, endpoints)) {
                String current = sameLocale(serverLocale, personalLocale)
                        ? localized(BukkitLanguageMessages.CURRENT,
                        MessageArgs.builder().untrusted("locale", serverLocale).build())
                        : localized(BukkitLanguageMessages.CURRENT_WITH_PERSONAL,
                        MessageArgs.builder()
                                .untrusted("locale", serverLocale)
                                .untrusted("personal", personalLocale)
                                .build());
                entries.add(link(sender, localized(BukkitLanguageMessages.SERVER_DEFAULT), command + " server",
                        current));
            }
            entries.add(link(sender, localized(BukkitLanguageMessages.RESET_YOUR_LANGUAGE), command + " self reset",
                    localized(BukkitLanguageMessages.RESET_DESCRIPTION)));
        } else if (canSelectServer(sender, endpoints)) {
            entries.add(link(sender, localized(BukkitLanguageMessages.SERVER_DEFAULT), command + " server",
                    localized(BukkitLanguageMessages.CURRENT,
                            MessageArgs.builder().untrusted("locale", serverLocale).build())));
        }
        if (sender instanceof Player && canSelectServer(sender, endpoints)) {
            entries.add(link(sender, localized(BukkitLanguageMessages.EDIT_MESSAGES), command + " server edit",
                    localized(BukkitLanguageMessages.EDIT_DESCRIPTION)));
        }
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                command, command, "/" + options.command(), entries,
                styled(localized(BukkitLanguageMessages.NO_CONTROLS), options.theme().muted()),
                1, Math.max(1, entries.size()));
        DirectorMiniMenu.deliverContent(sender, menu, options.theme(), options.textResolver());
    }

    private void showVolmitHome(CommandSender sender) {
        String entry = link(sender, "Plugins", "/volmit plugins",
                "Open shared Volmit plugin tools.");
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/volmit", "/volmit", List.of(entry), "No shared tools are available.", 1, 1);
        DirectorMiniMenu.deliverContent(sender, menu, options.theme(), options.textResolver());
    }

    private void showPluginTools(CommandSender sender, List<Endpoint> endpoints) {
        ArrayList<String> entries = new ArrayList<>();
        if (canSelectServer(sender, endpoints)) {
            entries.add(link(sender, "Languages", "/volmit plugins languages",
                    "Change the server default language for all registered Volmit plugins"));
        }
        List<DebugEndpoint> registeredDebug = debugEndpoints();
        List<DebugEndpoint> debug = registeredDebug.stream()
                .filter(endpoint -> sender.hasPermission(endpoint.permission()))
                .toList();
        if (!debug.isEmpty()) {
            entries.add(link(sender, "Debug reports", "/volmit plugins debug",
                    "Create diagnostic reports for " + debug.size() + " registered Volmit plugin"
                            + (debug.size() == 1 ? "" : "s")));
        }
        LinkedHashSet<String> debugNames = new LinkedHashSet<>();
        for (DebugEndpoint endpoint : registeredDebug) {
            debugNames.add(endpoint.name().toLowerCase(Locale.ROOT));
        }
        LinkedHashSet<String> languageNames = new LinkedHashSet<>();
        for (Endpoint endpoint : endpoints) {
            languageNames.add(endpoint.name().toLowerCase(Locale.ROOT));
            String capabilities = debugNames.contains(endpoint.name().toLowerCase(Locale.ROOT))
                    ? "Language and debug" : "Language";
            entries.add(styled(endpoint.name() + " - " + capabilities, options.theme().description()));
        }
        for (DebugEndpoint endpoint : registeredDebug) {
            if (!languageNames.contains(endpoint.name().toLowerCase(Locale.ROOT))) {
                entries.add(styled(endpoint.name() + " - Debug", options.theme().description()));
            }
        }
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/volmit plugins", "/volmit plugins", "/volmit", entries,
                "No available plugin tools.", 1, Math.max(1, entries.size()));
        DirectorMiniMenu.deliverContent(sender, menu, options.theme(), options.textResolver());
    }

    private void executeDebug(CommandSender sender, String[] arguments) {
        List<DebugEndpoint> debug = debugEndpoints();
        if (arguments.length == 2) {
            showDebug(sender, debug, 1);
            return;
        }
        if (arguments.length == 3 && arguments[2].startsWith("page=")) {
            try {
                showDebug(sender, debug, Integer.parseInt(arguments[2].substring(5)));
            } catch (NumberFormatException exception) {
                message(sender, "Use a numeric debug page.");
            }
            return;
        }
        if (arguments.length < 3 || arguments.length > 4) {
            message(sender, "Usage: /volmit plugins debug [plugin|all] [upload=true|false]");
            return;
        }
        Boolean upload = arguments.length == 3 ? Boolean.TRUE : parseBoolean(arguments[3]);
        if (upload == null) {
            message(sender, "Use upload=true or upload=false.");
            return;
        }
        if (arguments[2].equalsIgnoreCase("all")) {
            executeAllDebug(sender, debug, upload);
            return;
        }
        DebugEndpoint selected = debug.stream()
                .filter(endpoint -> endpoint.name().equalsIgnoreCase(arguments[2]))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            message(sender, "No registered debug provider matches " + arguments[2] + ".");
            return;
        }
        if (!sender.hasPermission(selected.permission())) {
            message(sender, "Missing permission: " + selected.permission());
            return;
        }
        selected.request().accept(sender, upload);
    }

    private void executeAllDebug(CommandSender sender, List<DebugEndpoint> endpoints, boolean upload) {
        List<DebugEndpoint> allowed = endpoints.stream()
                .filter(endpoint -> sender.hasPermission(endpoint.permission()))
                .toList();
        if (allowed.isEmpty()) {
            message(sender, "No debug reports are available to you.");
            return;
        }
        ArrayList<CompletableFuture<Map<String, String>>> pending = new ArrayList<>(allowed.size());
        for (DebugEndpoint endpoint : allowed) {
            if (endpoint.aggregate() == null) {
                pending.add(CompletableFuture.completedFuture(debugFailure(endpoint,
                        "Update this plugin to support combined debug reports.")));
                continue;
            }
            try {
                CompletableFuture<Map<String, String>> request = endpoint.aggregate().apply(sender, upload);
                if (request == null) {
                    pending.add(CompletableFuture.completedFuture(debugFailure(endpoint,
                            "The debug provider returned no result.")));
                    continue;
                }
                pending.add(request.handle((result, failure) -> failure == null
                        ? normalizeDebugResult(endpoint, result)
                        : debugFailure(endpoint, Objects.requireNonNullElse(
                        failure.getMessage(), failure.getClass().getSimpleName()))));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Unable to start the " + endpoint.name() + " debug report from /volmit", exception);
                pending.add(CompletableFuture.completedFuture(debugFailure(endpoint,
                        "Unable to start the debug report; check the server console.")));
            }
        }
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            List<Map<String, String>> results = pending.stream().map(CompletableFuture::join).toList();
            reply(sender, () -> showAllDebugResults(sender, results));
        });
    }

    private void showAllDebugResults(CommandSender sender, List<Map<String, String>> results) {
        ArrayList<String> savedReports = new ArrayList<>();
        ArrayList<String> savedPaths = new ArrayList<>();
        ArrayList<String> uploadedReports = new ArrayList<>();
        ArrayList<String> uploadedUrls = new ArrayList<>();
        ArrayList<String> reportNotices = new ArrayList<>();
        for (Map<String, String> result : results) {
            String name = result.getOrDefault("name", "Unknown plugin");
            String version = result.getOrDefault("version", "unknown");
            if (!"success".equals(result.get("status"))) {
                reportNotices.add(styled(name + " v" + version + " - "
                        + result.getOrDefault("error", "The report failed."), options.theme().required()));
                continue;
            }
            String path = result.getOrDefault("path", "");
            if (!path.isEmpty()) {
                savedReports.add(debugPathEntry(name, version, path));
                savedPaths.add(path);
            }
            String notice = result.getOrDefault("notice", "");
            if (!notice.isEmpty()) {
                reportNotices.add(styled(name + " - " + notice, options.theme().required()));
            }
            String url = result.getOrDefault("url", "");
            if (!url.isEmpty()) {
                uploadedReports.add(debugUrlEntry(name, version, url));
                uploadedUrls.add(name + " - " + url);
            }
        }
        ArrayList<String> entries = new ArrayList<>(
                savedReports.size() + uploadedReports.size() + reportNotices.size() + 2);
        entries.addAll(savedReports);
        entries.addAll(uploadedReports);
        entries.addAll(reportNotices);
        if (!uploadedUrls.isEmpty()) {
            entries.add(bulkDebugCopyEntry(
                    "Copy all mclo.gs links",
                    "Copy every uploaded report URL",
                    uploadedUrls
            ));
        }
        if (!savedPaths.isEmpty()) {
            entries.add(bulkDebugCopyEntry(
                    "Copy all local paths",
                    "Copy every saved report path",
                    savedPaths
            ));
        }
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/volmit plugins debug all", "/volmit plugins debug all", "/volmit plugins debug", entries,
                "No debug reports were created.", 1, Math.max(1, entries.size()));
        DirectorMiniMenu.deliverContent(sender, menu, options.theme(), options.textResolver());
    }

    private String debugPathEntry(String name, String version, String path) {
        DirectorMiniMenu.Theme theme = options.theme();
        ComponentText content = ComponentText.markup(
                "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">"
                        + DirectorMiniMenu.escapeText(name + " v" + version) + "</gradient>"
                        + "<" + theme.muted() + "> - </" + theme.muted() + ">"
                        + "<" + theme.description() + ">"
                        + DirectorMiniMenu.escapeText(path) + "</" + theme.description() + ">"
        );
        ComponentText hover = ComponentText.markup(
                "<" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(name + " debug report")
                        + "</" + theme.primaryRight() + "><reset>\n"
                        + "<" + theme.description() + ">✎ <font:minecraft:uniform>Click to copy the local report path."
                        + "</font></" + theme.description() + "><reset>\n"
                        + "<" + theme.optional() + ">✒ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(path) + "</font></" + theme.optional() + ">"
        );
        return entry(content.clickCopyToClipboard(path).hover(hover));
    }

    private String debugUrlEntry(String name, String version, String value) {
        DirectorMiniMenu.Theme theme = options.theme();
        URI url;
        try {
            url = URI.create(value);
        } catch (IllegalArgumentException exception) {
            return styled(name + " v" + version + " - The upload returned an invalid URL.", theme.required());
        }
        ComponentText content = ComponentText.markup(
                "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">Open "
                        + DirectorMiniMenu.escapeText(name + " report") + "</gradient>"
                        + "<" + theme.muted() + "> - </" + theme.muted() + ">"
                        + "<" + theme.description() + ">" + DirectorMiniMenu.escapeText(value)
                        + "</" + theme.description() + ">"
        );
        return entry(content.clickOpenUrl(url).hover(urlHover(
                "Open " + name + " report", "Open the uploaded diagnostic report", value)));
    }

    private String bulkDebugCopyEntry(String label, String description, List<String> values) {
        DirectorMiniMenu.Theme theme = options.theme();
        ComponentText content = ComponentText.markup(
                "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">"
                        + DirectorMiniMenu.escapeText(label) + "</gradient>"
                        + "<" + theme.muted() + "> - </" + theme.muted() + ">"
                        + "<" + theme.description() + ">" + DirectorMiniMenu.escapeText(description)
                        + "</" + theme.description() + ">"
        );
        ComponentText hover = ComponentText.markup(
                "<" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(label)
                        + "</" + theme.primaryRight() + "><reset>\n"
                        + "<" + theme.description() + ">✎ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(description) + ".</font></" + theme.description() + "><reset>\n"
                        + "<" + theme.optional() + ">✒ <font:minecraft:uniform>"
                        + values.size() + (values.size() == 1 ? " item" : " items")
                        + " separated by new lines</font></" + theme.optional() + ">"
        );
        return entry(content.clickCopyToClipboard(String.join("\n", values)).hover(hover));
    }

    private static Map<String, String> normalizeDebugResult(DebugEndpoint endpoint, Map<String, String> result) {
        if (result == null) {
            return debugFailure(endpoint, "The debug provider returned no result.");
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        normalized.put("status", Objects.toString(result.get("status"), "failure"));
        normalized.put("name", Objects.toString(result.get("name"), endpoint.name()));
        normalized.put("version", Objects.toString(result.get("version"), endpoint.version()));
        normalized.put("path", Objects.toString(result.get("path"), ""));
        normalized.put("url", Objects.toString(result.get("url"), ""));
        normalized.put("notice", Objects.toString(result.get("notice"), ""));
        normalized.put("error", Objects.toString(result.get("error"), "The report failed."));
        return Map.copyOf(normalized);
    }

    private static Map<String, String> debugFailure(DebugEndpoint endpoint, String error) {
        return Map.of(
                "status", "failure",
                "name", endpoint.name(),
                "version", endpoint.version(),
                "error", error
        );
    }

    private void showDebug(CommandSender sender, List<DebugEndpoint> endpoints, int requestedPage) {
        List<DebugEndpoint> allowed = endpoints.stream()
                .filter(endpoint -> sender.hasPermission(endpoint.permission()))
                .toList();
        ArrayList<String> entries = new ArrayList<>();
        if (!allowed.isEmpty()) {
            entries.add(link(sender, "All plugins", "/volmit plugins debug all",
                    "Create reports for every permitted debug provider."));
        }
        for (DebugEndpoint endpoint : allowed) {
            String command = "/volmit plugins debug " + endpoint.name();
            entries.add(link(sender, endpoint.name() + " v" + endpoint.version(), command,
                    "Create and save a " + endpoint.name() + " diagnostic report"));
        }
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/volmit plugins debug", "/volmit plugins debug", "/volmit plugins", entries,
                "No debug reports are available to you.", requestedPage, PAGE_SIZE);
        DirectorMiniMenu.deliverContent(sender, menu, options.theme(), options.textResolver());
    }

    private List<DebugEndpoint> allowedDebugEndpoints(CommandSender sender) {
        return debugEndpoints().stream()
                .filter(endpoint -> sender.hasPermission(endpoint.permission()))
                .toList();
    }

    private List<DebugEndpoint> debugEndpoints() {
        ArrayList<DebugEndpoint> endpoints = new ArrayList<>();
        for (RegisteredServiceProvider<?> registration : plugin.getServer().getServicesManager().getRegistrations(Map.class)) {
            if (registration.getPlugin().isEnabled() && registration.getProvider() instanceof Map<?, ?> values
                    && "1".equals(values.get("volmit.debug.protocol"))) {
                DebugEndpoint endpoint = debugEndpoint(values);
                if (endpoint != null) {
                    endpoints.add(endpoint);
                }
            }
        }
        endpoints.sort(Comparator.comparing(DebugEndpoint::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(endpoints);
    }

    private void select(CommandSender sender, Selection selection, String locale) {
        if (selection.scope().equals("server") && locale.equalsIgnoreCase("reset")) {
            message(sender, "Select a locale such as en_US for the server default.");
            return;
        }
        if (!locale.equalsIgnoreCase("reset") && commonLocales(selection.endpoints()).stream().noneMatch(candidate ->
                sameLocale(candidate, locale))) {
            message(sender, "Language " + locale + " is not available for every selected plugin.");
            return;
        }
        UUID playerId = sender instanceof Player player ? player.getUniqueId() : null;
        message(sender, "Preparing language " + locale + " for " + selection.label() + "...");
        for (Endpoint endpoint : selection.endpoints()) {
            CompletableFuture<String> pending = selection.scope().equals("self")
                    ? endpoint.self().apply(playerId, locale)
                    : endpoint.server().apply(locale);
            pending.whenComplete((appliedLocale, failure) -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Unable to select " + locale + " for " + endpoint.name(), failure);
                }
                reply(sender, () -> selectionFeedback(sender, selection.scope(), endpoint.name(), locale, appliedLocale, failure));
            });
        }
    }

    private void selectionFeedback(CommandSender sender, String scope, String name, String requested, String applied, Throwable failure) {
        if (failure != null) {
            message(sender, name + ": unable to save the language selection; check the server console.");
        } else if (!sameLocale(requested, applied)) {
            message(sender, name + ": " + requested + " is unavailable; using English (en_US).");
        } else {
            message(sender, name + ": " + (scope.equals("self") ? "your language" : "server language")
                    + " is now " + (applied.equalsIgnoreCase("reset") ? "the server default" : applied) + ".");
        }
    }

    private void reply(CommandSender sender, Runnable message) {
        if (closed || !plugin.isEnabled()) {
            return;
        }
        if (sender instanceof Player player) {
            FoliaScheduler.runEntity(plugin, player, message);
        } else {
            FoliaScheduler.runGlobal(plugin, message);
        }
    }

    private String selectedLocale(CommandSender sender, String scope, List<Endpoint> endpoints) {
        UUID playerId = scope.equals("self") && sender instanceof Player player ? player.getUniqueId() : null;
        String selected = null;
        for (Endpoint endpoint : endpoints) {
            String locale = playerId == null ? endpoint.defaultLocale().get() : endpoint.selected().apply(playerId);
            if (selected != null && !selected.equalsIgnoreCase(locale)) {
                return "mixed";
            }
            selected = locale;
        }
        return selected == null ? VolmitLocales.ENGLISH : selected;
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Map<?, ?> values : providers()) {
            endpoints.add(endpoint(values));
        }
        endpoints.sort(Comparator.comparing(Endpoint::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(endpoints);
    }

    private List<Map<?, ?>> providers() {
        List<Map<?, ?>> providers = new ArrayList<>();
        for (RegisteredServiceProvider<?> registration : plugin.getServer().getServicesManager().getRegistrations(Map.class)) {
            if (registration.getPlugin().isEnabled() && registration.getProvider() instanceof Map<?, ?> values
                    && PROTOCOL.equals(values.get(PROTOCOL_KEY))) {
                providers.add(values);
            }
        }
        return providers;
    }

    @SuppressWarnings("unchecked")
    private static Endpoint endpoint(Map<?, ?> values) {
        return new Endpoint((String) values.get("name"), (String) values.get("permission"),
                (Supplier<List<String>>) values.get("locales"), (Supplier<String>) values.get("default"),
                (Function<UUID, String>) values.get("selected"),
                (BiFunction<UUID, String, CompletableFuture<String>>) values.get("self"),
                (Function<String, CompletableFuture<String>>) values.get("server"));
    }

    @SuppressWarnings("unchecked")
    private static DebugEndpoint debugEndpoint(Map<?, ?> values) {
        Object request = values.get("request");
        Object aggregate = values.get("request.aggregate");
        Object name = values.get("name");
        Object permission = values.get("permission");
        if (!(name instanceof String pluginName) || pluginName.isBlank()
                || !(permission instanceof String permissionNode) || permissionNode.isBlank()
                || !(request instanceof BiConsumer<?, ?>)) {
            return null;
        }
        return new DebugEndpoint(
                pluginName,
                Objects.toString(values.get("version"), "unknown"),
                permissionNode,
                (BiConsumer<CommandSender, Boolean>) request,
                aggregate instanceof BiFunction<?, ?, ?>
                        ? (BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>>) aggregate
                        : null
        );
    }

    private static List<String> commonLocales(List<Endpoint> endpoints) {
        LinkedHashSet<String> locales = new LinkedHashSet<>();
        boolean first = true;
        for (Endpoint endpoint : endpoints) {
            Collection<String> available = endpoint.locales().get();
            if (first) {
                locales.addAll(available);
                first = false;
            } else {
                locales.retainAll(available);
            }
        }
        return List.copyOf(locales);
    }

    private static List<String> matching(Collection<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static boolean sameLocale(String first, String second) {
        return first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
    }

    private void message(CommandSender sender, String text) {
        ComponentMessenger.send(sender, ComponentText.markup(styled(text, options.theme().description())));
    }

    private String link(CommandSender sender, String label, String command, String hover) {
        if (!(sender instanceof Player)) {
            return styled(label + ": " + command, options.theme().description());
        }
        DirectorMiniMenu.Theme theme = options.theme();
        ComponentText content = ComponentText.markup(
                "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">"
                        + DirectorMiniMenu.escapeText(label) + "</gradient>"
                        + "<" + theme.muted() + "> - </" + theme.muted() + ">"
                        + "<" + theme.description() + ">" + DirectorMiniMenu.escapeText(hover)
                        + "</" + theme.description() + ">"
        );
        return entry(content.clickRunCommand(command).hover(commandHover(label, hover, command)));
    }

    private String languageLink(CommandSender sender, String locale, String name, boolean selected, String command) {
        if (!(sender instanceof Player)) {
            return styled((selected ? "[selected] " : "") + locale + " - " + name + ": " + command,
                    options.theme().description());
        }
        DirectorMiniMenu.Theme theme = options.theme();
        ComponentText content = ComponentText.markup(
                (selected ? "<green>✔</green> " : "<dark_gray>•</dark_gray> ")
                        + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">"
                        + DirectorMiniMenu.escapeText(locale) + "</gradient>"
                        + "<" + theme.muted() + "> - </" + theme.muted() + ">"
                        + "<" + theme.description() + ">" + DirectorMiniMenu.escapeText(name)
                        + "</" + theme.description() + ">"
        );
        return entry(content.clickRunCommand(command).hover(commandHover(
                locale + " - " + name, localized(BukkitLanguageMessages.SELECT_DESCRIPTION), command)));
    }

    private String localized(TextKey key) {
        return localized(key, MessageArgs.empty());
    }

    private String localized(TextKey key, MessageArgs arguments) {
        String rendered;
        try {
            rendered = options.textResolver().resolve(key, arguments);
        } catch (IllegalArgumentException exception) {
            rendered = null;
        }
        if (rendered == null) {
            rendered = DirectorTextResolver.ENGLISH.resolve(key, arguments);
        }
        return ComponentText.markup(rendered).plain();
    }

    private String entry(ComponentText content) {
        return ComponentText.markup("<" + options.theme().muted() + ">⇀</" + options.theme().muted() + "> ")
                .append(content)
                .miniMessage();
    }

    private ComponentText commandHover(String title, String description, String command) {
        DirectorMiniMenu.Theme theme = options.theme();
        return ComponentText.markup(
                "<" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(title)
                        + "</" + theme.primaryRight() + "><reset>\n"
                        + "<" + theme.description() + ">✎ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(description) + "</font></" + theme.description() + "><reset>\n"
                        + "<" + theme.optional() + ">✒ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText("Command: " + command) + "</font></" + theme.optional() + ">"
        );
    }

    private ComponentText urlHover(String title, String description, String url) {
        DirectorMiniMenu.Theme theme = options.theme();
        return ComponentText.markup(
                "<" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(title)
                        + "</" + theme.primaryRight() + "><reset>\n"
                        + "<" + theme.description() + ">✎ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(description) + "</font></" + theme.description() + "><reset>\n"
                        + "<" + theme.optional() + ">✒ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(url) + "</font></" + theme.optional() + ">"
        );
    }

    private String styled(String text, String color) {
        return "<" + options.theme().muted() + ">⇀ </" + options.theme().muted() + "><" + color + ">"
                + DirectorMiniMenu.escapeText(text) + "</" + color + ">";
    }

    private static Boolean parseBoolean(String input) {
        String value = input;
        int separator = value.indexOf('=');
        if (separator >= 0) {
            if (!value.substring(0, separator).equalsIgnoreCase("upload")) {
                return null;
            }
            value = value.substring(separator + 1);
        }
        if (value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        return null;
    }

    public record Options(String command, String adminPermission, DirectorMiniMenu.Theme theme,
                          DirectorTextResolver textResolver, PluginLanguageEditor.Options editor,
                          LanguageEditFeedback editorFeedback) {
        public Options(String command, String adminPermission, DirectorMiniMenu.Theme theme,
                       DirectorTextResolver textResolver, PluginLanguageEditor.Options editor) {
            this(command, adminPermission, theme, textResolver, editor, null);
        }

        public Options {
            if (command == null || !command.matches("[a-z0-9][a-z0-9_-]*")) {
                throw new IllegalArgumentException("A root command name without a slash is required");
            }
            Objects.requireNonNull(adminPermission, "adminPermission");
            Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(textResolver, "textResolver");
            Objects.requireNonNull(editor, "editor");
        }
    }

    @FunctionalInterface
    public interface LanguageEditFeedback {
        ComponentText saved(CommandSender sender, LanguageEditChange change);
    }

    public record LanguageEditChange(String locale, String key, String before, String after) {
        public LanguageEditChange {
            Objects.requireNonNull(locale, "locale");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }

    private record Selection(String scope, List<Endpoint> endpoints, String command, String label, boolean pluginOnly) {
    }

    private record Endpoint(
            String name,
            String permission,
            Supplier<List<String>> locales,
            Supplier<String> defaultLocale,
            Function<UUID, String> selected,
            BiFunction<UUID, String, CompletableFuture<String>> self,
            Function<String, CompletableFuture<String>> server
    ) {
    }

    private record DebugEndpoint(
            String name,
            String version,
            String permission,
            BiConsumer<CommandSender, Boolean> request,
            BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>> aggregate
    ) {
    }
}
