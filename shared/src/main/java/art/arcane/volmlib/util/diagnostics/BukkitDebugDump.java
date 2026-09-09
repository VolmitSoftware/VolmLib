package art.arcane.volmlib.util.diagnostics;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class BukkitDebugDump implements AutoCloseable {
    private static final String PROTOCOL_KEY = "volmit.debug.protocol";
    private static final String PROTOCOL = "1";
    private static final int MAXIMUM_FILE_ATTEMPTS = 10_000;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC);

    private final Plugin plugin;
    private volatile Options options;
    private final String permission;
    private final MclogsClient uploader;
    private final Map<String, Object> provider;
    private final AtomicReference<DumpOperation> activeOperation = new AtomicReference<>();
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
            DumpOperation operation = activeOperation.getAndSet(null);
            if (operation != null) {
                operation.cancel();
            }
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

    public synchronized void updateTheme(DirectorMiniMenu.Theme theme) {
        Presentation presentation = options.presentation();
        if (closed.get() || presentation == null) {
            return;
        }
        options = new Options(
                options.uploadEnabled(),
                options.contributor(),
                new Presentation(
                        presentation.command(),
                        presentation.parentCommand(),
                        Objects.requireNonNull(theme, "theme"),
                        presentation.textResolver()
                )
        );
    }

    public void request(CommandSender sender, boolean upload) {
        Objects.requireNonNull(sender, "sender");
        if (!sender.hasPermission(permission)) {
            message(sender, text(sender, BukkitDebugMessages.MISSING_PERMISSION,
                    MessageArgument.untrusted("permission", permission)));
            return;
        }
        if (closed.get()) {
            return;
        }
        DumpOperation operation = new DumpOperation(DebugResult.failure(pluginName(), pluginVersion(),
                text(sender, BukkitDebugMessages.PROVIDER_CLOSED)));
        if (!activeOperation.compareAndSet(null, operation)) {
            message(sender, text(sender, BukkitDebugMessages.ALREADY_PREPARING,
                    MessageArgument.untrusted("plugin", plugin.getName())));
            return;
        }
        CompletableFuture<DebugResult> result = operation.result();
        result.thenAccept(debugResult -> reply(sender, () -> deliverResult(sender, debugResult)));
        if (closed.get()) {
            cancel(operation);
            return;
        }
        message(sender, text(sender, BukkitDebugMessages.PREPARING,
                MessageArgument.untrusted("plugin", plugin.getName())));
        scheduleCapture(sender, upload, operation);
    }

    private CompletableFuture<Map<String, String>> requestAggregate(CommandSender sender, Boolean upload) {
        Objects.requireNonNull(sender, "sender");
        if (!sender.hasPermission(permission)) {
            return CompletableFuture.completedFuture(DebugResult.failure(pluginName(), pluginVersion(),
                    text(sender, BukkitDebugMessages.MISSING_PERMISSION,
                            MessageArgument.untrusted("permission", permission))).export());
        }
        if (closed.get()) {
            return CompletableFuture.completedFuture(DebugResult.failure(pluginName(), pluginVersion(),
                    text(sender, BukkitDebugMessages.PROVIDER_CLOSED)).export());
        }
        DumpOperation operation = new DumpOperation(DebugResult.failure(pluginName(), pluginVersion(),
                text(sender, BukkitDebugMessages.PROVIDER_CLOSED)));
        if (!activeOperation.compareAndSet(null, operation)) {
            return CompletableFuture.completedFuture(DebugResult.failure(pluginName(), pluginVersion(),
                    text(sender, BukkitDebugMessages.ALREADY_PREPARING_SHORT)).export());
        }
        if (closed.get()) {
            cancel(operation);
        } else {
            scheduleCapture(sender, Boolean.TRUE.equals(upload), operation);
        }
        return operation.result().thenApply(DebugResult::export);
    }

    private void scheduleCapture(CommandSender sender, boolean upload, DumpOperation operation) {
        DebugTask task = new DebugTask(() -> capture(sender, upload, operation));
        if (!operation.registerCapture(task)) {
            return;
        }
        if (!active(operation)) {
            operation.discardCapture(task);
            return;
        }
        if (!FoliaScheduler.runGlobal(plugin, task)) {
            operation.discardCapture(task);
            complete(operation, DebugResult.failure(pluginName(), pluginVersion(),
                    text(sender, BukkitDebugMessages.SCHEDULE_FAILED)));
        }
    }

    private void capture(CommandSender sender, boolean upload, DumpOperation operation) {
        if (!active(operation)) {
            return;
        }
        try {
            BukkitDebugSnapshot snapshot = BukkitDebugSnapshot.capture(plugin, sender);
            if (!active(operation)) {
                return;
            }
            DebugDumpContributor.Report details = captureDetails();
            if (!active(operation)) {
                return;
            }
            boolean shouldUpload = upload && options.uploadEnabled().getAsBoolean();
            DebugTask task = new DebugTask(() -> writeAndUpload(
                    sender, snapshot, details, shouldUpload, operation));
            if (!operation.registerWriter(task)) {
                return;
            }
            if (!FoliaScheduler.runAsync(plugin, task)) {
                operation.discardWriter(task);
                complete(operation, DebugResult.failure(pluginName(), pluginVersion(),
                        text(sender, BukkitDebugMessages.WRITER_SCHEDULE_FAILED)));
            }
        } catch (RuntimeException exception) {
            if (active(operation)) {
                plugin.getLogger().log(Level.SEVERE,
                        "Unable to capture " + plugin.getName() + " diagnostic state", exception);
                complete(operation, DebugResult.failure(pluginName(), pluginVersion(),
                        text(sender, BukkitDebugMessages.CAPTURE_FAILED)));
            }
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

    private void writeAndUpload(CommandSender sender, BukkitDebugSnapshot snapshot, DebugDumpContributor.Report details,
                                boolean upload, DumpOperation operation) {
        if (!active(operation)) {
            return;
        }
        try {
            String report = DebugDumpReport.create(snapshot, renderDetails(details));
            if (!active(operation)) {
                return;
            }
            Path file = write(snapshot.dataDirectory(), snapshot.generatedAt(), report);
            if (!active(operation)) {
                return;
            }
            String path = file.toAbsolutePath().normalize().toString();
            plugin.getLogger().info("Saved debug dump: " + path);
            DebugResult outcome;
            if (upload && active(operation)) {
                outcome = upload(sender, snapshot, report, path, operation);
            } else {
                outcome = DebugResult.saved(pluginName(), pluginVersion(), path);
            }
            complete(operation, outcome);
        } catch (IOException | RuntimeException exception) {
            if (active(operation)) {
                plugin.getLogger().log(Level.SEVERE,
                        "Unable to write " + plugin.getName() + " debug dump", exception);
                complete(operation, DebugResult.failure(pluginName(), pluginVersion(),
                        text(sender, BukkitDebugMessages.WRITE_FAILED)));
            }
        }
    }

    private Path write(Path dataDirectory, Instant generatedAt, String report) throws IOException {
        Path root = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        Path directory = root.resolve("debug").normalize();
        requireDirectChild(root, directory);
        requireSafeDirectory(directory, false);
        Files.createDirectories(directory);
        requireSafeDirectory(directory, true);
        String name = plugin.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        String version = plugin.getDescription().getVersion().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        String baseName = name + "-v" + version + "-debugdump-" + FILE_TIME.format(generatedAt);
        byte[] content = Objects.requireNonNullElse(report, "").getBytes(StandardCharsets.UTF_8);
        for (int attempt = 1; attempt <= MAXIMUM_FILE_ATTEMPTS; attempt++) {
            requireSafeDirectory(directory, true);
            String suffix = attempt == 1 ? "" : "-" + attempt;
            Path target = directory.resolve(baseName + suffix + ".txt").normalize();
            requireDirectChild(directory, target);
            try {
                writeNewFile(target, content);
                return target;
            } catch (FileAlreadyExistsException collision) {
                continue;
            }
        }
        throw new IOException("Unable to allocate a unique debug report in " + directory);
    }

    private void requireSafeDirectory(Path directory, boolean required) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (required) {
                throw new IOException("Debug output directory disappeared: " + directory);
            }
            return;
        }
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Debug output cannot be a symbolic link: " + directory);
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Debug output is not a regular directory: " + directory);
        }
    }

    private void requireDirectChild(Path directory, Path target) throws IOException {
        if (!target.startsWith(directory) || !directory.equals(target.getParent())) {
            throw new IOException("Debug output escaped its directory: " + target);
        }
    }

    private void writeNewFile(Path target, byte[] content) throws IOException {
        boolean created = false;
        Object createdFileKey = null;
        try (FileChannel channel = FileChannel.open(
                target,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
        )) {
            created = true;
            BasicFileAttributes attributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw new IOException("Debug output is not a regular file: " + target);
            }
            createdFileKey = attributes.fileKey();
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        } catch (IOException | RuntimeException exception) {
            if (created && createdFileKey != null) {
                try {
                    BasicFileAttributes current = Files.readAttributes(
                            target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (current.isRegularFile() && createdFileKey.equals(current.fileKey())) {
                        Files.deleteIfExists(target);
                    }
                } catch (IOException | RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
    }

    private DebugResult upload(CommandSender sender, BukkitDebugSnapshot snapshot, String report, String path,
                               DumpOperation operation) {
        try {
            URI url = uploader.publish(report,
                    "VolmitSoftware - " + snapshot.pluginName() + " - v" + snapshot.pluginVersion(),
                    "VolmitSoftware/" + snapshot.pluginName() + "/" + snapshot.pluginVersion());
            if (active(operation)) {
                plugin.getLogger().info("Debug dump uploaded: " + url);
            }
            return DebugResult.uploaded(pluginName(), pluginVersion(), path, url.toString());
        } catch (InterruptedException exception) {
            if (!operation.terminal()) {
                Thread.currentThread().interrupt();
                plugin.getLogger().log(Level.WARNING,
                        plugin.getName() + " debug dump upload was interrupted", exception);
            }
            return DebugResult.savedWithNotice(pluginName(), pluginVersion(), path,
                    text(sender, BukkitDebugMessages.UPLOAD_INTERRUPTED));
        } catch (IOException | RuntimeException exception) {
            if (active(operation)) {
                plugin.getLogger().log(Level.WARNING,
                        "Unable to upload " + plugin.getName() + " debug dump to mclo.gs", exception);
            }
            return DebugResult.savedWithNotice(pluginName(), pluginVersion(), path,
                    text(sender, BukkitDebugMessages.UPLOAD_FAILED));
        }
    }

    private boolean active(DumpOperation operation) {
        return !closed.get() && !operation.terminal() && activeOperation.get() == operation;
    }

    private void complete(DumpOperation operation, DebugResult result) {
        if (activeOperation.compareAndSet(operation, null)) {
            operation.complete(result);
        }
    }

    private void cancel(DumpOperation operation) {
        activeOperation.compareAndSet(operation, null);
        operation.cancel();
    }

    private void deliverResult(CommandSender sender, DebugResult result) {
        if (!result.successful()) {
            deliver(sender, List.of(themed(result.error(), theme().required())));
            return;
        }
        ArrayList<ComponentText> entries = new ArrayList<>(savedEntries(sender, result.path()));
        if (!result.notice().isEmpty()) {
            entries.add(themed(result.notice(), theme().required()));
        }
        if (!result.url().isEmpty()) {
            URI url = URI.create(result.url());
            entries.add(themed(text(sender, BukkitDebugMessages.UPLOADED_AS,
                            MessageArgument.untrusted("plugin", plugin.getName()),
                            MessageArgument.untrusted("version", pluginVersion())),
                    theme().description()));
            entries.add(action(
                    text(sender, BukkitDebugMessages.OPEN_LABEL, MessageArgument.untrusted("url", url)),
                    text(sender, BukkitDebugMessages.OPEN_DESCRIPTION),
                    url.toString())
                    .clickOpenUrl(url));
        }
        deliver(sender, entries);
    }

    private List<ComponentText> savedEntries(CommandSender sender, String path) {
        return List.of(
                themed(text(sender, BukkitDebugMessages.SAVED,
                        MessageArgument.untrusted("plugin", plugin.getName()),
                        MessageArgument.untrusted("path", path)), theme().description()),
                action(text(sender, BukkitDebugMessages.COPY_PATH),
                        text(sender, BukkitDebugMessages.COPY_PATH_DESCRIPTION), path).clickCopyToClipboard(path)
        );
    }

    private void message(CommandSender sender, String text) {
        reply(sender, () -> deliver(sender, List.of(themed(text, theme().description()))));
    }

    private void deliver(CommandSender sender, List<ComponentText> content) {
        if (closed.get() || !plugin.isEnabled()) {
            return;
        }
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
        DirectorMiniMenu.deliverContent(sender, menu, theme(), presentation.textResolver());
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

    private String text(CommandSender sender, TextKey key, MessageArgument... arguments) {
        return audience(sender, () -> {
            Presentation presentation = options.presentation();
            DirectorTextResolver resolver = presentation == null
                    ? DirectorTextResolver.ENGLISH
                    : presentation.textResolver();
            return resolver.resolve(key, arguments);
        });
    }

    private <T> T audience(CommandSender sender, Supplier<T> action) {
        return sender instanceof Player player
                ? LanguageAudience.call(player.getUniqueId(), action)
                : action.get();
    }

    private void reply(CommandSender sender, Runnable delivery) {
        if (closed.get() || !plugin.isEnabled()) {
            return;
        }
        Runnable localized = sender instanceof Player player
                ? () -> LanguageAudience.run(player.getUniqueId(), delivery)
                : delivery;
        Runnable guarded = () -> {
            if (!closed.get() && plugin.isEnabled()) {
                localized.run();
            }
        };
        boolean scheduled = sender instanceof Player player
                ? FoliaScheduler.runEntity(plugin, player, guarded)
                : FoliaScheduler.runGlobal(plugin, guarded);
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

    private static final class DumpOperation {
        private final DebugResult closedResult;
        private final CompletableFuture<DebugResult> result = new CompletableFuture<>();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicReference<DebugTask> captureTask = new AtomicReference<>();
        private final AtomicReference<DebugTask> writerTask = new AtomicReference<>();

        private DumpOperation(DebugResult closedResult) {
            this.closedResult = Objects.requireNonNull(closedResult, "closedResult");
        }

        private CompletableFuture<DebugResult> result() {
            return result;
        }

        private boolean terminal() {
            return terminal.get();
        }

        private boolean registerCapture(DebugTask task) {
            return register(captureTask, task, false);
        }

        private boolean registerWriter(DebugTask task) {
            return register(writerTask, task, true);
        }

        private void discardCapture(DebugTask task) {
            discard(captureTask, task, false);
        }

        private void discardWriter(DebugTask task) {
            discard(writerTask, task, true);
        }

        private void complete(DebugResult outcome) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            captureTask.set(null);
            writerTask.set(null);
            result.complete(outcome);
        }

        private void cancel() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            DebugTask capture = captureTask.getAndSet(null);
            if (capture != null) {
                capture.cancel(false);
            }
            DebugTask writer = writerTask.getAndSet(null);
            if (writer != null) {
                writer.cancel(true);
            }
            result.complete(closedResult);
        }

        private boolean register(AtomicReference<DebugTask> slot, DebugTask task, boolean interrupt) {
            if (terminal.get()) {
                task.cancel(interrupt);
                return false;
            }
            if (!slot.compareAndSet(null, task)) {
                throw new IllegalStateException("A debug operation task was already registered");
            }
            if (terminal.get()) {
                slot.compareAndSet(task, null);
                task.cancel(interrupt);
                return false;
            }
            return true;
        }

        private void discard(AtomicReference<DebugTask> slot, DebugTask task, boolean interrupt) {
            if (slot.compareAndSet(task, null)) {
                task.cancel(interrupt);
            }
        }
    }

    private static final class DebugTask implements Runnable {
        private Runnable action;
        private Thread runner;
        private boolean running;
        private boolean cancelled;
        private boolean cancellationInterrupt;

        private DebugTask(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        @Override
        public void run() {
            Runnable scheduled;
            synchronized (this) {
                if (cancelled || running || action == null) {
                    return;
                }
                running = true;
                runner = Thread.currentThread();
                scheduled = action;
                action = null;
            }
            try {
                scheduled.run();
            } finally {
                synchronized (this) {
                    runner = null;
                    running = false;
                    if (cancellationInterrupt) {
                        Thread.interrupted();
                    }
                }
            }
        }

        private synchronized void cancel(boolean interrupt) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            action = null;
            if (interrupt && runner != null) {
                cancellationInterrupt = true;
                runner.interrupt();
            }
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
