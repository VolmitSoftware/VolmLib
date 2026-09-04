package art.arcane.volmlib.util.diagnostics;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.web.MclogsClient;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

public final class BukkitDebugDump implements AutoCloseable {
    private static final String PROTOCOL_KEY = "volmit.debug.protocol";
    private static final String PROTOCOL = "1";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC);

    private final Plugin plugin;
    private final Options options;
    private final String permission;
    private final MclogsClient uploader;
    private final Map<String, Object> provider;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private BukkitDebugDump(Plugin plugin, Options options) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.options = Objects.requireNonNull(options, "options");
        String permissionRoot = plugin.getName().toLowerCase(Locale.ROOT);
        String debugPermission = permissionRoot + ".debug";
        permission = plugin.getServer().getPluginManager().getPermission(debugPermission) == null
                ? permissionRoot + ".debugdump" : debugPermission;
        uploader = new MclogsClient();
        provider = new ConcurrentHashMap<>();
        provider.put(PROTOCOL_KEY, PROTOCOL);
        provider.put("name", plugin.getName());
        provider.put("version", plugin.getDescription().getVersion());
        provider.put("permission", permission);
        provider.put("request", (BiConsumer<CommandSender, Boolean>) this::request);
        provider.put("request.aggregate",
                (BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>>) this::requestAggregate);
    }

    public static BukkitDebugDump create(Plugin plugin) {
        return create(plugin, new Options(() -> true, () -> () -> ""));
    }

    public static BukkitDebugDump create(Plugin plugin, Options options) {
        BukkitDebugDump dumps = new BukkitDebugDump(plugin, options);
        if (plugin.getServer().getPluginManager().getPermission(dumps.permission) == null) {
            plugin.getServer().getPluginManager().addPermission(new Permission(
                    dumps.permission, "Create and upload a " + plugin.getName() + " diagnostic report", PermissionDefault.OP));
        }
        plugin.getServer().getServicesManager().register(Map.class, dumps.provider, plugin, ServicePriority.Normal);
        return dumps;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            plugin.getServer().getServicesManager().unregister(Map.class, provider);
        }
    }

    public String permission() {
        return permission;
    }

    public String pluginName() {
        return plugin.getName();
    }

    public String pluginVersion() {
        return plugin.getDescription().getVersion();
    }

    public void request(CommandSender sender, boolean upload) {
        Objects.requireNonNull(sender, "sender");
        if (!sender.hasPermission(permission)) {
            message(sender, "Missing permission: " + permission);
            return;
        }
        if (closed.get()) {
            return;
        }
        if (!active.compareAndSet(false, true)) {
            message(sender, "A debug dump is already being prepared for " + plugin.getName() + ".");
            return;
        }
        message(sender, "Preparing " + plugin.getName() + " debug dump...");
        CompletableFuture<DebugResult> result = new CompletableFuture<>();
        result.thenAccept(debugResult -> reply(sender, () -> deliverResult(sender, debugResult)));
        if (!FoliaScheduler.runGlobal(plugin, () -> capture(sender, upload, result))) {
            active.set(false);
            result.complete(DebugResult.failure(pluginName(), pluginVersion(),
                    "Unable to schedule the debug dump."));
        }
    }

    private CompletableFuture<Map<String, String>> requestAggregate(CommandSender sender, Boolean upload) {
        Objects.requireNonNull(sender, "sender");
        if (!sender.hasPermission(permission)) {
            return CompletableFuture.completedFuture(DebugResult.failure(pluginName(), pluginVersion(),
                    "Missing permission: " + permission).export());
        }
        if (closed.get()) {
            return CompletableFuture.completedFuture(DebugResult.failure(pluginName(), pluginVersion(),
                    "The debug provider is closed.").export());
        }
        if (!active.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(DebugResult.failure(pluginName(), pluginVersion(),
                    "A debug dump is already being prepared.").export());
        }
        CompletableFuture<DebugResult> result = new CompletableFuture<>();
        if (!FoliaScheduler.runGlobal(plugin, () -> capture(sender, Boolean.TRUE.equals(upload), result))) {
            active.set(false);
            result.complete(DebugResult.failure(pluginName(), pluginVersion(),
                    "Unable to schedule the debug dump."));
        }
        return result.thenApply(DebugResult::export);
    }

    private void capture(CommandSender sender, boolean upload, CompletableFuture<DebugResult> result) {
        if (closed.get()) {
            active.set(false);
            result.complete(DebugResult.failure(pluginName(), pluginVersion(),
                    "The debug provider closed before the report was captured."));
            return;
        }
        try {
            BukkitDebugSnapshot snapshot = BukkitDebugSnapshot.capture(plugin, sender);
            DebugDumpContributor.Report details = captureDetails();
            boolean shouldUpload = upload && options.uploadEnabled().getAsBoolean();
            if (!FoliaScheduler.runAsync(plugin, () -> writeAndUpload(snapshot, details, shouldUpload, result))) {
                active.set(false);
                result.complete(DebugResult.failure(pluginName(), pluginVersion(),
                        "Unable to schedule the debug dump writer."));
            }
        } catch (RuntimeException exception) {
            active.set(false);
            plugin.getLogger().log(Level.SEVERE, "Unable to capture " + plugin.getName() + " diagnostic state", exception);
            result.complete(DebugResult.failure(pluginName(), pluginVersion(),
                    "Unable to capture the debug dump; check the server console."));
        }
    }

    private DebugDumpContributor.Report captureDetails() {
        try {
            return Objects.requireNonNull(options.contributor().capture(), "diagnostic contributor report");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to capture " + plugin.getName() + " diagnostic details", exception);
            return () -> "Plugin diagnostic capture failed: " + exception.getClass().getSimpleName();
        }
    }

    private String renderDetails(DebugDumpContributor.Report details) {
        try {
            return Objects.requireNonNullElse(details.render(), "");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to format " + plugin.getName() + " diagnostic details", exception);
            return "Plugin diagnostic formatting failed: " + exception.getClass().getSimpleName();
        }
    }

    private void writeAndUpload(BukkitDebugSnapshot snapshot, DebugDumpContributor.Report details,
                                boolean upload, CompletableFuture<DebugResult> result) {
        try {
            if (closed.get()) {
                result.complete(DebugResult.failure(pluginName(), pluginVersion(),
                        "The debug provider closed before the report was written."));
                return;
            }
            String report = DebugDumpReport.create(snapshot, renderDetails(details));
            Path file = write(snapshot.dataDirectory(), snapshot.generatedAt(), report);
            String path = file.toAbsolutePath().normalize().toString();
            plugin.getLogger().info("Saved debug dump: " + path);
            if (upload && !closed.get()) {
                result.complete(upload(snapshot, report, path));
            } else {
                result.complete(DebugResult.saved(pluginName(), pluginVersion(), path));
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to write " + plugin.getName() + " debug dump", exception);
            result.complete(DebugResult.failure(pluginName(), pluginVersion(),
                    "Unable to write the debug dump; check the server console."));
        } finally {
            active.set(false);
        }
    }

    private Path write(Path dataDirectory, Instant generatedAt, String report) throws IOException {
        Path directory = dataDirectory.resolve("debug").toAbsolutePath().normalize();
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Debug output is not a regular directory: " + directory);
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Debug output cannot be a symbolic link: " + directory);
        }
        String name = plugin.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        String version = plugin.getDescription().getVersion().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        Path target = directory.resolve(name + "-v" + version + "-debugdump-"
                + FILE_TIME.format(generatedAt) + ".txt");
        AtomicFileIO.writeString(target, report);
        return target;
    }

    private DebugResult upload(BukkitDebugSnapshot snapshot, String report, String path) {
        try {
            URI url = uploader.publish(report,
                    "VolmitSoftware - " + snapshot.pluginName() + " - v" + snapshot.pluginVersion(),
                    "VolmitSoftware/" + snapshot.pluginName() + "/" + snapshot.pluginVersion());
            plugin.getLogger().info("Debug dump uploaded: " + url);
            return DebugResult.uploaded(pluginName(), pluginVersion(), path, url.toString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.WARNING, plugin.getName() + " debug dump upload was interrupted", exception);
            return DebugResult.savedWithNotice(pluginName(), pluginVersion(), path,
                    "Debug dump upload was interrupted; the local report is saved.");
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to upload " + plugin.getName() + " debug dump to mclo.gs", exception);
            return DebugResult.savedWithNotice(pluginName(), pluginVersion(), path,
                    "Debug dump upload failed; the local report is saved.");
        }
    }

    private void deliverResult(CommandSender sender, DebugResult result) {
        if (!result.successful()) {
            deliver(sender, List.of(themed(result.error(), theme().required())));
            return;
        }
        ArrayList<ComponentText> entries = new ArrayList<>(savedEntries(result.path()));
        if (!result.notice().isEmpty()) {
            entries.add(themed(result.notice(), theme().required()));
        }
        if (!result.url().isEmpty()) {
            URI url = URI.create(result.url());
            entries.add(themed("Uploaded as VolmitSoftware - " + plugin.getName() + " - v" + pluginVersion() + ".",
                    theme().description()));
            entries.add(action("Open: " + url, "Open the uploaded diagnostic report.", url.toString())
                    .clickOpenUrl(url));
        }
        deliver(sender, entries);
    }

    private List<ComponentText> savedEntries(String path) {
        return List.of(
                themed("Saved " + plugin.getName() + " debug dump to " + path + ".", theme().description()),
                action("Copy local path", "Copy the local report path.", path).clickCopyToClipboard(path)
        );
    }

    private void message(CommandSender sender, String text) {
        reply(sender, () -> deliver(sender, List.of(themed(text, theme().description()))));
    }

    private void deliver(CommandSender sender, List<ComponentText> content) {
        Presentation presentation = options.presentation();
        if (presentation == null) {
            for (ComponentText line : content) {
                ComponentMessenger.send(sender, line);
            }
            return;
        }
        List<String> entries = content.stream().map(this::entry).toList();
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                presentation.command(), presentation.command(), presentation.parentCommand(),
                entries, "", 1, Math.max(1, entries.size()));
        DirectorMiniMenu.deliverContent(sender, menu, presentation.theme(), presentation.textResolver());
    }

    private ComponentText themed(String text, String color) {
        return ComponentText.markup("<" + color + ">" + DirectorMiniMenu.escapeText(text) + "</" + color + ">");
    }

    private ComponentText action(String title, String description, String value) {
        DirectorMiniMenu.Theme theme = theme();
        ComponentText label = ComponentText.markup(
                "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">"
                        + DirectorMiniMenu.escapeText(title) + "</gradient>"
        );
        return label.hover(ComponentText.markup(
                "<" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(title)
                        + "</" + theme.primaryRight() + "><reset>\n"
                        + "<" + theme.description() + ">✎ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(description) + "</font></" + theme.description() + "><reset>\n"
                        + "<" + theme.optional() + ">✒ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(value) + "</font></" + theme.optional() + ">"
        ));
    }

    private String entry(ComponentText content) {
        DirectorMiniMenu.Theme theme = theme();
        return ComponentText.markup("<" + theme.muted() + ">⇀</" + theme.muted() + "> ")
                .append(content)
                .miniMessage();
    }

    private DirectorMiniMenu.Theme theme() {
        Presentation presentation = options.presentation();
        return presentation == null ? DirectorMiniMenu.Theme.adaptRed() : presentation.theme();
    }

    private void reply(CommandSender sender, Runnable delivery) {
        if (closed.get() || !plugin.isEnabled()) {
            return;
        }
        boolean scheduled = sender instanceof Player player
                ? FoliaScheduler.runEntity(plugin, player, delivery)
                : FoliaScheduler.runGlobal(plugin, delivery);
        if (!scheduled && !closed.get()) {
            plugin.getLogger().warning("Unable to schedule debug dump command feedback");
        }
    }

    public record Options(BooleanSupplier uploadEnabled, DebugDumpContributor contributor, Presentation presentation) {
        public Options(BooleanSupplier uploadEnabled, DebugDumpContributor contributor) {
            this(uploadEnabled, contributor, null);
        }

        public Options {
            Objects.requireNonNull(uploadEnabled, "uploadEnabled");
            Objects.requireNonNull(contributor, "contributor");
        }
    }

    public record Presentation(String command, String parentCommand, DirectorMiniMenu.Theme theme,
                               DirectorTextResolver textResolver) {
        public Presentation {
            if (command == null || !command.startsWith("/")) {
                throw new IllegalArgumentException("A debug command beginning with / is required");
            }
            if (parentCommand == null || !parentCommand.startsWith("/")) {
                throw new IllegalArgumentException("A parent command beginning with / is required");
            }
            Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(textResolver, "textResolver");
        }
    }

    private record DebugResult(String name, String version, String path, String url, String notice, String error) {
        private static DebugResult saved(String name, String version, String path) {
            return new DebugResult(name, version, path, "", "", "");
        }

        private static DebugResult savedWithNotice(String name, String version, String path, String notice) {
            return new DebugResult(name, version, path, "", notice, "");
        }

        private static DebugResult uploaded(String name, String version, String path, String url) {
            return new DebugResult(name, version, path, url, "", "");
        }

        private static DebugResult failure(String name, String version, String error) {
            return new DebugResult(name, version, "", "", "", error);
        }

        private boolean successful() {
            return error.isEmpty();
        }

        private Map<String, String> export() {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            values.put("status", successful() ? "success" : "failure");
            values.put("name", name);
            values.put("version", version);
            values.put("path", path);
            values.put("url", url);
            values.put("notice", notice);
            values.put("error", error);
            return Map.copyOf(values);
        }
    }
}
