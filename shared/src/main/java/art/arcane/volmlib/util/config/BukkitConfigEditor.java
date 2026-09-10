package art.arcane.volmlib.util.config;

import art.arcane.volmlib.util.bukkit.BukkitInventoryViews;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.inventorygui.BukkitInventoryShutdown;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class BukkitConfigEditor implements Listener, AutoCloseable {
    static final int PAGE_SIZE = 45;
    static final int MAXIMUM_INPUT_LENGTH = 16384;
    private static final int SIZE = 54;
    private static final int BACK = 45;
    private static final int PREVIOUS = 48;
    private static final int REFRESH = 49;
    private static final int NEXT = 50;
    private static final int CLOSE = 53;
    private static final long PROMPT_TICKS = 1200L;

    private final Plugin plugin;
    private final Options options;
    private final ExecutorService worker;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitInventoryShutdown.View> openInventories = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private BukkitConfigEditor(Plugin plugin, Options options) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.options = Objects.requireNonNull(options, "options");
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, plugin.getName() + "-config-editor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static BukkitConfigEditor register(Plugin plugin, Options options) {
        BukkitConfigEditor editor = new BukkitConfigEditor(plugin, options);
        plugin.getServer().getPluginManager().registerEvents(editor, plugin);
        return editor;
    }

    public void open(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (!(sender instanceof Player player)) {
            ComponentMessenger.send(sender, styled(options.presentation().textResolver()
                    .resolve(BukkitConfigMessages.PLAYER_ONLY), options.presentation().theme().description()));
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> openOwned(player));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        sessions.clear();
        prompts.clear();
        worker.shutdownNow();
        BukkitInventoryShutdown.drain(plugin, openInventories.values());
        openInventories.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(BukkitInventoryViews.top(event.getView()).getHolder() instanceof Holder holder) || holder.editor != this) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> {
            if (BukkitInventoryViews.top(player.getOpenInventory()).getHolder() == holder) {
                click(player, holder, slot);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (BukkitInventoryViews.top(event.getView()).getHolder() instanceof Holder holder && holder.editor == this) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID owner = event.getPlayer().getUniqueId();
        BukkitInventoryShutdown.View opened = openInventories.get(owner);
        if (opened != null && opened.inventory() == event.getInventory()) {
            openInventories.remove(owner, opened);
        }
        if (event.getInventory().getHolder() instanceof Holder holder && holder.editor == this
                && holder.session.view == holder.view && !prompts.containsKey(owner)) {
            sessions.remove(owner, holder.session);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || holder.editor != this) {
            cancel(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage();
        if (!FoliaScheduler.runEntity(plugin, player, () -> completePrompt(player, prompt, input), 0L,
                () -> sessions.remove(prompt.session().owner, prompt.session()))) {
            sessions.remove(prompt.session().owner, prompt.session());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID owner = event.getPlayer().getUniqueId();
        cancel(owner);
        openInventories.remove(owner);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        }
    }

    private void openOwned(Player player) {
        if (!allowed(player)) {
            return;
        }
        cancel(player.getUniqueId());
        Session session = new Session(player);
        sessions.put(player.getUniqueId(), session);
        show(session, new View(null, List.of(), 0));
        load(session);
    }

    private void load(Session session) {
        if (session.busy || !active(session) || !allowed(session.player)) {
            return;
        }
        session.busy = true;
        submit(session, options.loader()::load, document -> {
            session.busy = false;
            List<String> path = session.view.path();
            try {
                document.entries(path);
            } catch (IllegalArgumentException exception) {
                path = List.of();
            }
            show(session, new View(document, path, session.view.page()));
        });
    }

    private void click(Player player, Holder holder, int slot) {
        Session session = holder.session;
        if (!active(session) || !allowed(player)) {
            return;
        }
        if (slot == CLOSE) {
            cancel(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (session.busy) {
            return;
        }
        View view = holder.view;
        if (slot == REFRESH) {
            load(session);
        } else if (view.document() == null) {
            return;
        } else if (slot == BACK && !view.path().isEmpty()) {
            show(session, new View(view.document(), view.path().subList(0, view.path().size() - 1), 0));
        } else if (slot == PREVIOUS || slot == NEXT) {
            show(session, new View(view.document(), view.path(), view.page() + (slot == NEXT ? 1 : -1)));
        } else if (slot < PAGE_SIZE) {
            int index = view.page() * PAGE_SIZE + slot;
            if (index < holder.entries.size()) {
                select(session, holder.entries.get(index));
            }
        }
    }

    private void select(Session session, ConfigEditorDocument.Entry entry) {
        if (entry.kind() == ConfigEditorDocument.Kind.TABLE || entry.kind() == ConfigEditorDocument.Kind.TABLE_ARRAY) {
            show(session, new View(session.view.document(), entry.path(), 0));
        } else if (entry.kind() == ConfigEditorDocument.Kind.BOOLEAN) {
            save(session, session.view.document().edit(entry.path(), new JsonPrimitive(!entry.value().getAsBoolean())));
        } else {
            Prompt prompt = new Prompt(session, entry);
            prompts.put(session.player.getUniqueId(), prompt);
            session.player.closeInventory();
            message(session.player, BukkitConfigMessages.PROMPT,
                    MessageArgument.untrusted("path", displayPath(entry.path())));
            message(session.player, BukkitConfigMessages.CURRENT,
                    MessageArgument.untrusted("value", preview(entry.value())));
            message(session.player, guidance(entry.kind()));
            if (!FoliaScheduler.runEntity(plugin, session.player, () -> expire(prompt), PROMPT_TICKS,
                    () -> prompts.remove(session.player.getUniqueId(), prompt))) {
                prompts.remove(session.player.getUniqueId(), prompt);
                cancel(session.player.getUniqueId());
            }
        }
    }

    private void completePrompt(Player player, Prompt prompt, String input) {
        Session session = prompt.session();
        if (!active(session) || !allowed(player)) {
            return;
        }
        if (input.equalsIgnoreCase("cancel")) {
            show(session, session.view.copy());
            return;
        }
        if (input.length() > MAXIMUM_INPUT_LENGTH) {
            message(player, BukkitConfigMessages.TOO_LONG, MessageArgument.trusted("maximum", MAXIMUM_INPUT_LENGTH));
            show(session, session.view.copy());
            return;
        }
        try {
            ConfigEditorDocument document = session.view.document();
            JsonElement replacement = document.parseValue(prompt.entry().path(), input);
            save(session, document.edit(prompt.entry().path(), replacement));
        } catch (IOException | IllegalArgumentException failure) {
            message(player, BukkitConfigMessages.FAILED, MessageArgument.untrusted("reason", reason(failure)));
            show(session, session.view.copy());
        }
    }

    private void expire(Prompt prompt) {
        Session session = prompt.session();
        if (prompts.remove(session.player.getUniqueId(), prompt) && active(session) && allowed(session.player)) {
            message(session.player, BukkitConfigMessages.EXPIRED);
            show(session, session.view.copy());
        }
    }

    private void save(Session session, ConfigEditorDocument.Edit edit) {
        if (session.busy || !active(session) || !allowed(session.player)) {
            return;
        }
        session.busy = true;
        message(session.player, BukkitConfigMessages.SAVING);
        submit(session, () -> options.writer().save(edit), document -> {
            session.busy = false;
            message(session.player, BukkitConfigMessages.SAVED,
                    MessageArgument.untrusted("path", displayPath(edit.path())));
            show(session, new View(document, session.view.path(), session.view.page()));
        });
    }

    private void submit(Session session, Loader operation, Consumer<ConfigEditorDocument> result) {
        try {
            worker.execute(() -> {
                if (!active(session)) {
                    return;
                }
                try {
                    ConfigEditorDocument document = Objects.requireNonNull(operation.load(), "Loaded configuration");
                    deliver(session, () -> result.accept(document));
                } catch (Exception | LinkageError failure) {
                    plugin.getLogger().log(Level.SEVERE, "Unable to edit " + plugin.getName() + " configuration", failure);
                    deliver(session, () -> failed(session, failure));
                }
            });
        } catch (RejectedExecutionException failure) {
            if (active(session)) {
                failed(session, failure);
            }
        }
    }

    private void deliver(Session session, Runnable action) {
        if (active(session)) {
            if (!FoliaScheduler.runEntity(plugin, session.player, () -> {
                if (active(session) && allowed(session.player)) {
                    action.run();
                }
            }, 0L, () -> sessions.remove(session.owner, session))) {
                sessions.remove(session.owner, session);
            }
        }
    }

    private void failed(Session session, Throwable failure) {
        session.busy = false;
        message(session.player, BukkitConfigMessages.FAILED, MessageArgument.untrusted("reason", reason(failure)));
        show(session, session.view.copy());
    }

    private void show(Session session, View requested) {
        Player player = session.player;
        if (!active(session) || !allowed(player)) {
            return;
        }
        List<ConfigEditorDocument.Entry> entries = requested.document() == null ? List.of()
                : orderedEntries(requested.document(), requested.path());
        int maximum = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        View view = new View(requested.document(), requested.path(), Math.max(0, Math.min(requested.page(), maximum)));
        Holder holder = new Holder(this, session, view);
        holder.entries = entries;
        Inventory inventory = plugin.getServer().createInventory(holder, SIZE,
                text(player, BukkitConfigMessages.TITLE, MessageArgument.untrusted("plugin", plugin.getName())));
        holder.inventory = inventory;
        int start = view.page() * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, entries.size()); index++) {
            inventory.setItem(index - start, entryItem(player, entries.get(index), view));
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER,
                    text(player, view.document() == null ? BukkitConfigMessages.LOADING : BukkitConfigMessages.EMPTY), List.of()));
        }
        if (!view.path().isEmpty()) {
            inventory.setItem(BACK, item(Material.ARROW, text(player, BukkitConfigMessages.BACK), List.of()));
        }
        if (view.page() > 0) {
            inventory.setItem(PREVIOUS, item(Material.ARROW, text(player, BukkitConfigMessages.PREVIOUS), List.of()));
        }
        if (view.page() < maximum) {
            inventory.setItem(NEXT, item(Material.ARROW, text(player, BukkitConfigMessages.NEXT), List.of()));
        }
        inventory.setItem(REFRESH, item(Material.COMPASS, text(player, BukkitConfigMessages.REFRESH), List.of(
                text(player, BukkitConfigMessages.SECTION, MessageArgument.untrusted("path", displayPath(view.path()))),
                text(player, BukkitConfigMessages.PAGE, MessageArgument.trusted("page", view.page() + 1),
                        MessageArgument.trusted("pages", maximum + 1)))));
        inventory.setItem(CLOSE, item(Material.BARRIER, text(player, BukkitConfigMessages.CLOSE), List.of()));
        session.view = view;
        player.openInventory(inventory);
        if (BukkitInventoryViews.top(player.getOpenInventory()) == inventory) {
            BukkitInventoryShutdown.View opened = new BukkitInventoryShutdown.View(player, inventory);
            openInventories.put(session.owner, opened);
            if (!active(session)) {
                player.closeInventory();
                openInventories.remove(session.owner, opened);
            }
        } else {
            sessions.remove(player.getUniqueId(), session);
        }
    }

    private ItemStack entryItem(Player player, ConfigEditorDocument.Entry entry, View view) {
        boolean section = entry.kind() == ConfigEditorDocument.Kind.TABLE || entry.kind() == ConfigEditorDocument.Kind.TABLE_ARRAY;
        String name = entry.name();
        if (view.document().value(view.path()).isJsonArray()) {
            name = text(player, BukkitConfigMessages.ENTRY, MessageArgument.trusted("number", Integer.parseInt(name) + 1));
        }
        ArrayList<String> lore = new ArrayList<>();
        lore.add(displayPath(entry.path()));
        if (!section) {
            lore.add(text(player, BukkitConfigMessages.CURRENT, MessageArgument.untrusted("value", preview(entry.value()))));
        }
        lore.add(text(player, section ? BukkitConfigMessages.OPEN
                : entry.kind() == ConfigEditorDocument.Kind.BOOLEAN ? BukkitConfigMessages.TOGGLE : BukkitConfigMessages.EDIT));
        if (!section && entry.kind() != ConfigEditorDocument.Kind.BOOLEAN) {
            lore.add(text(player, guidance(entry.kind())));
        }
        return item(icon(entry), name, lore);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(styled(name, options.presentation().theme().primaryLeft()).legacy());
            ArrayList<String> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(styled(line, options.presentation().theme().description()).legacy());
            }
            meta.setLore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean allowed(Player player) {
        if (closed || !plugin.isEnabled() || !player.isOnline()) {
            return false;
        }
        CommandSender sender = player;
        if (sender.hasPermission(options.presentation().permission())) {
            return true;
        }
        cancel(player.getUniqueId());
        message(player, BukkitConfigMessages.NO_PERMISSION);
        if (BukkitInventoryViews.top(player.getOpenInventory()).getHolder() instanceof Holder holder && holder.editor == this) {
            player.closeInventory();
        }
        return false;
    }

    private boolean active(Session session) {
        return !closed && sessions.get(session.owner) == session;
    }

    private void cancel(UUID owner) {
        sessions.remove(owner);
        prompts.remove(owner);
    }

    private void message(Player player, TextKey key, MessageArgument... arguments) {
        ComponentMessenger.send(player, styled(text(player, key, arguments), options.presentation().theme().description()));
    }

    private String text(Player player, TextKey key, MessageArgument... arguments) {
        return LanguageAudience.call(player.getUniqueId(), () -> options.presentation().textResolver().resolve(key, arguments));
    }

    private static ComponentText styled(String text, String color) {
        return ComponentText.component(Component.text(text).color(TextColor.fromHexString(color)));
    }

    static List<ConfigEditorDocument.Entry> orderedEntries(ConfigEditorDocument document, List<String> path) {
        ArrayList<ConfigEditorDocument.Entry> entries = new ArrayList<>(document.entries(path));
        if (document.value(path).isJsonObject()) {
            entries.sort(Comparator.comparing(ConfigEditorDocument.Entry::name));
        }
        return List.copyOf(entries);
    }

    static String preview(JsonElement value) {
        String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
        String singleLine = text.replace("\r", "\\r").replace("\n", "\\n");
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 117) + "...";
    }

    static String displayPath(List<String> path) {
        return path.isEmpty() ? "/" : String.join(" / ", path);
    }

    private static Material icon(ConfigEditorDocument.Entry entry) {
        return switch (entry.kind()) {
            case TABLE, TABLE_ARRAY -> Material.CHEST;
            case BOOLEAN -> entry.value().getAsBoolean() ? Material.LIME_DYE : Material.GRAY_DYE;
            case INTEGER, DECIMAL -> Material.COMPARATOR;
            case TEXT -> Material.NAME_TAG;
            case LIST -> Material.WRITABLE_BOOK;
        };
    }

    private static TextKey guidance(ConfigEditorDocument.Kind kind) {
        return switch (kind) {
            case INTEGER -> BukkitConfigMessages.INTEGER;
            case DECIMAL -> BukkitConfigMessages.DECIMAL;
            case LIST -> BukkitConfigMessages.LIST;
            default -> BukkitConfigMessages.TEXT;
        };
    }

    private static String reason(Throwable failure) {
        return Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getSimpleName());
    }

    public record Options(Loader loader, Writer writer, Presentation presentation) {
        public Options {
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(writer, "writer");
            Objects.requireNonNull(presentation, "presentation");
        }
    }

    public record Presentation(String permission, DirectorMiniMenu.Theme theme, DirectorTextResolver textResolver) {
        public Presentation {
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(textResolver, "textResolver");
        }
    }

    @FunctionalInterface
    public interface Loader {
        ConfigEditorDocument load() throws Exception;
    }

    @FunctionalInterface
    public interface Writer {
        ConfigEditorDocument save(ConfigEditorDocument.Edit edit) throws Exception;
    }

    private static final class Session {
        private final Player player;
        private final UUID owner;
        private View view;
        private boolean busy;

        private Session(Player player) {
            this.player = player;
            owner = player.getUniqueId();
        }
    }

    private record View(ConfigEditorDocument document, List<String> path, int page) {
        private View {
            path = List.copyOf(path);
        }

        private View copy() {
            return new View(document, path, page);
        }
    }

    private record Prompt(Session session, ConfigEditorDocument.Entry entry) {
    }

    private static final class Holder implements InventoryHolder {
        private final BukkitConfigEditor editor;
        private final Session session;
        private final View view;
        private final UUID owner;
        private List<ConfigEditorDocument.Entry> entries;
        private Inventory inventory;

        private Holder(BukkitConfigEditor editor, Session session, View view) {
            this.editor = editor;
            this.session = session;
            this.view = view;
            owner = session.player.getUniqueId();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
