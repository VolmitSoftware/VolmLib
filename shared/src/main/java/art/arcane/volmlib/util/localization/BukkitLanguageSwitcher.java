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
import java.util.function.BiFunction;
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
            List<Endpoint> selected = endpoints();
            if (selected.isEmpty()) {
                message(sender, "No Volmit language providers are available.");
                return true;
            }
            if (!allowed(sender, "server", selected)) {
                return true;
            }
            if (arguments.length < 2 || !arguments[0].equalsIgnoreCase("plugins")
                    || !arguments[1].equalsIgnoreCase("languages") || arguments.length > 3) {
                message(sender, "Usage: /volmit plugins languages [locale]");
                return true;
            }
            Selection selection = new Selection("server", selected, "/volmit plugins languages", "all Volmit plugins", false);
            executeSelection(sender, selection, arguments.length == 3 ? arguments[2] : null);
            return true;
        });
    }

    List<String> completeVolmit(CommandSender sender, String[] arguments) {
        List<Endpoint> selected = endpoints();
        if (closed || arguments.length == 0 || !canSelectServer(sender, selected)) {
            return List.of();
        }
        if (arguments.length == 1) {
            return matching(List.of("plugins"), arguments[0]);
        }
        if (!arguments[0].equalsIgnoreCase("plugins")) {
            return List.of();
        }
        if (arguments.length == 2) {
            return matching(List.of("languages"), arguments[1]);
        }
        return arguments.length == 3 && arguments[1].equalsIgnoreCase("languages")
                ? matching(commonLocales(selected), arguments[2]) : List.of();
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
        Map<String, Object> values = new LinkedHashMap<>();
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
        return Map.copyOf(values);
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
        String scope = arguments.length == 0
                ? (canSelectSelf(sender, selected) ? "self" : "server")
                : arguments[0].toLowerCase(Locale.ROOT);
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
        lines.add(DirectorMiniMenu.banner(base, page, theme));
        String current = selectedLocale(sender, selection.scope(), selection.endpoints());
        lines.add(styled("Current: " + current, theme.description()));
        if (selection.pluginOnly()) {
            String pluginBase = "/" + options.command() + " language";
            if (canSelectSelf(sender, selection.endpoints())) {
                lines.add(link(sender, "Your preference", pluginBase + " self", "Change only your language"));
            }
            if (canSelectServer(sender, selection.endpoints())) {
                lines.add(link(sender, "Server default", pluginBase + " server", "Change the default for players without an override"));
                if (sender instanceof Player) {
                    lines.add(link(sender, "Edit language messages", pluginBase + " server edit", "Open the per-language message editor"));
                }
            }
        }
        for (int index = page.startIndex(); index < page.endIndex(); index++) {
            String locale = locales.get(index);
            String name = VolmitLocales.displayName(locale).orElse(locale);
            lines.add(link(sender, (locale.equalsIgnoreCase(current) ? "* " : "") + locale + " - " + name,
                    base + " " + locale, "Select " + name));
            if (sender instanceof Player && selection.pluginOnly() && selection.scope().equals("server")) {
                lines.add(link(sender, "Edit " + locale, base + " edit " + locale, "Edit messages without changing language selections"));
            }
        }
        if (selection.scope().equals("self")) {
            lines.add(link(sender, "Use server default", base + " reset", "Remove your saved language preference"));
        }
        lines.add(DirectorMiniMenu.paginationBar(page, base, theme, options.textResolver()));
        DirectorMiniMenu.deliver(sender, lines);
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
        return "<hover:show_text:'" + attribute(hover) + "'><click:run_command:'" + attribute(command) + "'>"
                + styled(label, options.theme().primaryRight()) + "</click></hover>";
    }

    private String styled(String text, String color) {
        return "<" + options.theme().muted() + ">⇀ </" + options.theme().muted() + "><" + color + ">"
                + DirectorMiniMenu.escapeText(text) + "</" + color + ">";
    }

    private static String attribute(String text) {
        return DirectorMiniMenu.escapeText(text).replace("'", "\\'");
    }

    public record Options(String command, String adminPermission, DirectorMiniMenu.Theme theme,
                          DirectorTextResolver textResolver, PluginLanguageEditor.Options editor) {
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
}
