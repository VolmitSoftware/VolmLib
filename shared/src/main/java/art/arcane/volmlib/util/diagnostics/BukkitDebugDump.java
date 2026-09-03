package art.arcane.volmlib.util.diagnostics;

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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

public final class BukkitDebugDump implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final Plugin plugin;
    private final Options options;
    private final String permission;
    private final MclogsClient uploader;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private BukkitDebugDump(Plugin plugin, Options options) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.options = Objects.requireNonNull(options, "options");
        permission = plugin.getName().toLowerCase(Locale.ROOT) + ".debugdump";
        uploader = new MclogsClient();
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
        return dumps;
    }

    @Override
    public void close() {
        closed.set(true);
    }

    public String permission() {
        return permission;
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
        if (!FoliaScheduler.runGlobal(plugin, () -> capture(sender, upload))) {
            active.set(false);
            message(sender, "Unable to schedule the debug dump.");
        }
    }

    private void capture(CommandSender sender, boolean upload) {
        if (closed.get()) {
            active.set(false);
            return;
        }
        try {
            BukkitDebugSnapshot snapshot = BukkitDebugSnapshot.capture(plugin, sender);
            DebugDumpContributor.Report details = captureDetails();
            boolean shouldUpload = upload && options.uploadEnabled().getAsBoolean();
            if (!FoliaScheduler.runAsync(plugin, () -> writeAndUpload(sender, snapshot, details, shouldUpload))) {
                active.set(false);
                message(sender, "Unable to schedule the debug dump writer.");
            }
        } catch (RuntimeException exception) {
            active.set(false);
            plugin.getLogger().log(Level.SEVERE, "Unable to capture " + plugin.getName() + " diagnostic state", exception);
            message(sender, "Unable to capture the debug dump; check the server console.");
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

    private void writeAndUpload(CommandSender sender, BukkitDebugSnapshot snapshot,
                                DebugDumpContributor.Report details, boolean upload) {
        try {
            if (closed.get()) {
                return;
            }
            String report = DebugDumpReport.create(snapshot, renderDetails(details));
            Path file = write(snapshot.dataDirectory(), snapshot.generatedAt(), report);
            String path = "debug/" + file.getFileName();
            reply(sender, () -> {
                ComponentMessenger.sendLiteral(sender, "Saved " + plugin.getName() + " debug dump: " + path);
                if (sender instanceof Player player) {
                    ComponentMessenger.sendCopyToClipboard(player, ComponentText.literal("[Copy path]"), path,
                            ComponentText.literal("Copy the report path to your clipboard"));
                }
            });
            plugin.getLogger().info("Saved debug dump: " + path);
            if (upload && !closed.get()) {
                upload(sender, snapshot, report);
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to write " + plugin.getName() + " debug dump", exception);
            message(sender, "Unable to write the debug dump; check the server console.");
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
        String prefix = plugin.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        Path target = directory.resolve(prefix + "-debugdump-" + FILE_TIME.format(generatedAt)
                + "-" + UUID.randomUUID() + ".txt");
        AtomicFileIO.writeString(target, report);
        return target;
    }

    private void upload(CommandSender sender, BukkitDebugSnapshot snapshot, String report) {
        try {
            URI url = uploader.publish(report, snapshot.pluginName() + " " + snapshot.pluginVersion(),
                    snapshot.pluginName() + "/" + snapshot.pluginVersion());
            reply(sender, () -> {
                ComponentText link = ComponentText.literal("Debug dump: " + url);
                if (sender instanceof Player player) {
                    ComponentMessenger.sendOpenUrl(player, link, url, ComponentText.literal("Open the debug report"));
                    ComponentMessenger.sendCopyToClipboard(player, ComponentText.literal("[Copy link]"), url.toString(),
                            ComponentText.literal("Copy the debug report link to your clipboard"));
                } else {
                    ComponentMessenger.send(sender, link);
                }
            });
            plugin.getLogger().info("Debug dump uploaded: " + url);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.WARNING, plugin.getName() + " debug dump upload was interrupted", exception);
            message(sender, "Debug dump upload was interrupted; the local report is saved.");
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to upload " + plugin.getName() + " debug dump to mclo.gs", exception);
            message(sender, "Debug dump upload failed; the local report is saved.");
        }
    }

    private void message(CommandSender sender, String text) {
        reply(sender, () -> ComponentMessenger.sendLiteral(sender, text));
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

    public record Options(BooleanSupplier uploadEnabled, DebugDumpContributor contributor) {
        public Options {
            Objects.requireNonNull(uploadEnabled, "uploadEnabled");
            Objects.requireNonNull(contributor, "contributor");
        }
    }
}
