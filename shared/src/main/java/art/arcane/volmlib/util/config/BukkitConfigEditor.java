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
import art.arcane.volmlib.util.plugin.LegacyLoreLayout;
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
import org.bukkit.event.inventory.ClickType;
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
import java.math.BigDecimal;
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
import java.util.function.Function;
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
    private volatile EditorLayout layout;
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
        open(sender, null);
    }

    public void open(CommandSender sender, Consumer<Player> returnAction) {
        Objects.requireNonNull(sender, "sender");
        if (!(sender instanceof Player player)) {
            ComponentMessenger.send(sender, styled(options.presentation().textResolver()
                    .resolve(BukkitConfigMessages.PLAYER_ONLY), options.presentation().theme().description()));
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> openOwned(player, returnAction));
    }

    public synchronized void configureLayout(EditorLayout layout) {
        if (closed || !sessions.isEmpty()) {
            throw new IllegalStateException("Configure the editor layout before opening it");
        }
        this.layout = Objects.requireNonNull(layout, "layout");
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
        ClickType clickType = event.getClick();
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> {
            if (BukkitInventoryViews.top(player.getOpenInventory()).getHolder() == holder) {
                LanguageAudience.run(player.getUniqueId(), () -> click(player, holder, slot, clickType));
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

    private void openOwned(Player player, Consumer<Player> returnAction) {
        if (!allowed(player)) {
            return;
        }
        cancel(player.getUniqueId());
        Session session = new Session(player, returnAction);
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

    private void click(Player player, Holder holder, int slot, ClickType clickType) {
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
        if (slot == BACK && view.path().isEmpty() && session.returnAction != null) {
            cancel(player.getUniqueId());
            player.closeInventory();
            session.returnAction.accept(player);
        } else if (slot == REFRESH && layout == null) {
            load(session);
        } else if (view.document() == null) {
            return;
        } else if (slot == BACK && !view.path().isEmpty()) {
            show(session, new View(view.document(), view.path().subList(0, view.path().size() - 1), 0));
        } else if (slot == PREVIOUS || slot == NEXT) {
            show(session, new View(view.document(), view.path(), view.page() + (slot == NEXT ? 1 : -1)));
        } else if (slot < PAGE_SIZE) {
            int ordinal = holder.contentSlots.indexOf(slot);
            if (ordinal < 0) {
                return;
            }
            int index = view.page() * pageSize(view.path()) + ordinal;
            if (index < holder.entries.size()) {
                select(session, holder.entries.get(index), clickType);
            } else if (index - holder.entries.size() < holder.shortcuts.size()) {
                RootShortcut shortcut = holder.shortcuts.get(index - holder.entries.size());
                cancel(player.getUniqueId());
                shortcut.action().accept(player);
            }
        }
    }

    private void select(Session session, ConfigEditorDocument.Entry entry, ClickType clickType) {
        EntryPresentation presentation = entryPresentation(entry.path());
        if (presentation != null && presentation.action() != null) {
            cancel(session.owner);
            session.player.closeInventory();
            presentation.action().accept(session.player);
            return;
        }
        if (entry.kind() == ConfigEditorDocument.Kind.TABLE || entry.kind() == ConfigEditorDocument.Kind.TABLE_ARRAY) {
            show(session, new View(session.view.document(), entry.path(), 0));
        } else if (entry.kind() == ConfigEditorDocument.Kind.BOOLEAN) {
            save(session, session.view.document().edit(entry.path(), new JsonPrimitive(!entry.value().getAsBoolean())));
        } else if (presentation != null && presentation.numeric() != null && numeric(entry.kind())
                && numericClick(clickType)) {
            try {
                JsonPrimitive replacement = adjustedValue(entry, presentation.numeric(), clickType);
                if (!replacement.equals(entry.value())) {
                    save(session, session.view.document().edit(entry.path(), replacement));
                }
            } catch (ArithmeticException | IllegalArgumentException failure) {
                message(session.player, BukkitConfigMessages.FAILED, MessageArgument.untrusted("reason", reason(failure)));
            }
        } else {
            Prompt prompt = new Prompt(session, entry);
            prompts.put(session.player.getUniqueId(), prompt);
            session.player.closeInventory();
            message(session.player, BukkitConfigMessages.PROMPT,
                    MessageArgument.untrusted("path", presentation == null ? displayPath(entry.path())
                            : ComponentText.markup(text(session.player, presentation.name())).plain()));
            message(session.player, BukkitConfigMessages.CURRENT,
                    MessageArgument.untrusted("value", preview(entry.value())));
            message(session.player, inputGuidance(entry.kind(), presentation));
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
            JsonElement replacement = parseInput(document, prompt.entry(), entryPresentation(prompt.entry().path()), input);
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
                : presentedEntries(requested.document(), requested.path(), layout);
        List<RootShortcut> shortcuts = layout != null && requested.path().isEmpty() && requested.document() != null
                ? layout.shortcuts() : List.of();
        int pageSize = pageSize(requested.path());
        int total = entries.size() + shortcuts.size();
        int maximum = Math.max(0, (total - 1) / pageSize);
        View view = new View(requested.document(), requested.path(), Math.max(0, Math.min(requested.page(), maximum)));
        Holder holder = new Holder(this, session, view);
        holder.entries = entries;
        holder.shortcuts = shortcuts;
        Inventory inventory = plugin.getServer().createInventory(holder, SIZE, title(player, view));
        holder.inventory = inventory;
        if (layout != null) {
            ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
            for (int slot = 0; slot < SIZE; slot++) {
                inventory.setItem(slot, filler);
            }
        }
        int start = view.page() * pageSize;
        int count = Math.min(pageSize, total - start);
        holder.contentSlots = contentSlots(view.path(), count);
        for (int index = start; index < start + count; index++) {
            ItemStack entry = index < entries.size() ? entryItem(player, entries.get(index), view)
                    : shortcutItem(player, shortcuts.get(index - entries.size()));
            inventory.setItem(holder.contentSlots.get(index - start), entry);
        }
        if (total == 0) {
            inventory.setItem(22, item(Material.PAPER,
                    text(player, view.document() == null ? BukkitConfigMessages.LOADING : BukkitConfigMessages.EMPTY), List.of()));
        }
        if (!view.path().isEmpty() || session.returnAction != null) {
            inventory.setItem(BACK, item(Material.ARROW,
                    navigationName(player, BukkitConfigMessages.BACK, options.presentation().theme().primaryRight()), List.of()));
        }
        if (view.page() > 0) {
            inventory.setItem(PREVIOUS, item(Material.ARROW,
                    navigationName(player, BukkitConfigMessages.PREVIOUS, options.presentation().theme().primaryRight()), List.of()));
        }
        if (view.page() < maximum) {
            inventory.setItem(NEXT, item(Material.ARROW,
                    navigationName(player, BukkitConfigMessages.NEXT, options.presentation().theme().primaryRight()), List.of()));
        }
        if (layout == null) {
            inventory.setItem(REFRESH, item(Material.COMPASS, text(player, BukkitConfigMessages.REFRESH), List.of(
                    text(player, BukkitConfigMessages.SECTION, MessageArgument.untrusted("path", displayPath(view.path()))),
                    text(player, BukkitConfigMessages.PAGE, MessageArgument.trusted("page", view.page() + 1),
                            MessageArgument.trusted("pages", maximum + 1)))));
        }
        inventory.setItem(CLOSE, item(Material.BARRIER,
                navigationName(player, BukkitConfigMessages.CLOSE, options.presentation().theme().required()), List.of()));
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
        EntryPresentation presentation = entryPresentation(entry.path());
        if (presentation != null) {
            return presentedItem(player, entry, presentation, section);
        }
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

    private ItemStack presentedItem(Player player, ConfigEditorDocument.Entry entry,
                                    EntryPresentation presentation, boolean section) {
        ArrayList<String> lore = new ArrayList<>();
        lore.add(text(player, presentation.description()));
        if (!section) {
            lore.add(text(player, BukkitConfigMessages.CURRENT, MessageArgument.untrusted("value", preview(entry.value()))));
        }
        if (presentation.action() == null) {
            if (presentation.numeric() != null && numeric(entry.kind())) {
                lore.add(text(player, BukkitConfigMessages.ADJUST,
                        MessageArgument.untrusted("step", BigDecimal.valueOf(presentation.numeric().step()).stripTrailingZeros().toPlainString())));
            } else {
                lore.add(text(player, section ? BukkitConfigMessages.OPEN
                        : entry.kind() == ConfigEditorDocument.Kind.BOOLEAN ? BukkitConfigMessages.TOGGLE : BukkitConfigMessages.EDIT));
                if (!section && entry.kind() != ConfigEditorDocument.Kind.BOOLEAN) {
                    lore.add(text(player, inputGuidance(entry.kind(), presentation)));
                }
            }
        }
        Material material = entry.kind() == ConfigEditorDocument.Kind.BOOLEAN ? icon(entry) : presentation.icon();
        return item(material, text(player, presentation.name()), lore);
    }

    private ItemStack shortcutItem(Player player, RootShortcut shortcut) {
        return item(shortcut.icon(), text(player, shortcut.name()), List.of(text(player, shortcut.description())));
    }

    private String title(Player player, View view) {
        if (layout == null) {
            return text(player, BukkitConfigMessages.TITLE, MessageArgument.untrusted("plugin", plugin.getName()));
        }
        ComponentText title = styledMarkup(text(player, layout.title()), "#404040");
        if (view.path().isEmpty()) {
            return title.legacy();
        }
        EntryPresentation presentation = entryPresentation(view.path());
        ComponentText section = presentation == null
                ? styled(view.path().get(view.path().size() - 1), "#404040")
                : styledMarkup(text(player, presentation.name()), "#404040");
        return section.legacy();
    }

    private EntryPresentation entryPresentation(List<String> path) {
        return layout == null ? null : layout.entries().apply(path);
    }

    private String navigationName(Player player, TextKey key, String color) {
        return layout == null ? text(player, key) : styledMarkup(text(player, key), color).legacy();
    }

    static List<ConfigEditorDocument.Entry> presentedEntries(ConfigEditorDocument document, List<String> path, EditorLayout layout) {
        if (layout == null) {
            return orderedEntries(document, path);
        }
        ArrayList<ConfigEditorDocument.Entry> entries = new ArrayList<>();
        for (ConfigEditorDocument.Entry entry : document.entries(path)) {
            if (layout.entries().apply(entry.path()) != null) {
                entries.add(entry);
            }
        }
        if (document.value(path).isJsonObject()) {
            entries.sort(Comparator.comparingInt((ConfigEditorDocument.Entry entry) -> {
                return layout.entries().apply(entry.path()).order();
            }).thenComparing(ConfigEditorDocument.Entry::name));
        }
        return List.copyOf(entries);
    }

    private int pageSize(List<String> path) {
        return layout != null && path.isEmpty() ? 8 : PAGE_SIZE;
    }

    private List<Integer> contentSlots(List<String> path, int count) {
        if (layout != null && path.isEmpty()) {
            return categorySlots(count);
        }
        ArrayList<Integer> slots = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((layout == null ? styled(name, options.presentation().theme().primaryLeft())
                    : styledMarkup(name, options.presentation().theme().primaryLeft())).legacy());
            ArrayList<String> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                if (layout == null) {
                    lines.add(styled(line, options.presentation().theme().description()).legacy());
                } else {
                    lines.addAll(LegacyLoreLayout.wrap(styledMarkup(line, options.presentation().theme().description()).legacy(), 44));
                }
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
        String message = text(player, key, arguments);
        ComponentMessenger.send(player, layout == null ? styled(message, options.presentation().theme().description())
                : styledMarkup(message, options.presentation().theme().description()));
    }

    private String text(Player player, TextKey key, MessageArgument... arguments) {
        return LanguageAudience.call(player.getUniqueId(), () -> options.presentation().textResolver().resolve(key, arguments));
    }

    private static ComponentText styled(String text, String color) {
        return ComponentText.component(Component.text(text).color(TextColor.fromHexString(color)));
    }

    private static ComponentText styledMarkup(String text, String color) {
        return ComponentText.markup(text).colorIfAbsent(color);
    }

    static List<Integer> categorySlots(int count) {
        if (count < 0 || count > 8) {
            throw new IllegalArgumentException("A category page holds at most eight entries");
        }
        if (count == 0) {
            return List.of();
        }
        int rows = (count + 3) / 4;
        int remaining = count;
        ArrayList<Integer> slots = new ArrayList<>(count);
        for (int row = 0; row < rows; row++) {
            int rowSize = (remaining + rows - row - 1) / (rows - row);
            int firstColumn = 5 - rowSize;
            for (int index = 0; index < rowSize; index++) {
                slots.add((2 + row) * 9 + firstColumn + index * 2);
            }
            remaining -= rowSize;
        }
        return List.copyOf(slots);
    }

    static JsonPrimitive adjustedValue(ConfigEditorDocument.Entry entry, NumericControl control, ClickType click) {
        BigDecimal step = BigDecimal.valueOf(control.step()).multiply(BigDecimal.valueOf(click.isShiftClick() ? 10L : 1L));
        if (click.isRightClick()) {
            step = step.negate();
        }
        BigDecimal adjusted = new BigDecimal(entry.value().getAsString()).add(step)
                .max(BigDecimal.valueOf(control.minimum())).min(BigDecimal.valueOf(control.maximum()));
        if (entry.kind() == ConfigEditorDocument.Kind.INTEGER && control.step() == Math.rint(control.step())) {
            return new JsonPrimitive(adjusted.longValueExact());
        }
        BigDecimal decimal = adjusted.stripTrailingZeros();
        return new JsonPrimitive(decimal.scale() < 1 ? decimal.setScale(1) : decimal);
    }

    private static boolean numeric(ConfigEditorDocument.Kind kind) {
        return kind == ConfigEditorDocument.Kind.INTEGER || kind == ConfigEditorDocument.Kind.DECIMAL;
    }

    static JsonElement parseInput(ConfigEditorDocument document, ConfigEditorDocument.Entry entry,
                                  EntryPresentation presentation, String input) throws IOException {
        return decimalControl(entry.kind(), presentation) ? ConfigEditorDocument.parseDecimal(input.strip())
                : document.parseValue(entry.path(), input);
    }

    private static boolean decimalControl(ConfigEditorDocument.Kind kind, EntryPresentation presentation) {
        return numeric(kind) && presentation != null && presentation.numeric() != null
                && presentation.numeric().step() != Math.rint(presentation.numeric().step());
    }

    private static boolean numericClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
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

    static TextKey inputGuidance(ConfigEditorDocument.Kind kind, EntryPresentation presentation) {
        if (presentation != null && presentation.inputGuidance() != null) {
            return presentation.inputGuidance();
        }
        return decimalControl(kind, presentation) ? BukkitConfigMessages.DECIMAL : guidance(kind);
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

    public record EditorLayout(TextKey title, Function<List<String>, EntryPresentation> entries, List<RootShortcut> shortcuts) {
        public EditorLayout {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(entries, "entries");
            shortcuts = List.copyOf(shortcuts);
        }
    }

    public record EntryPresentation(TextKey name, TextKey description, Material icon, int order,
                                    NumericControl numeric, TextKey inputGuidance, Consumer<Player> action) {
        public EntryPresentation {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(icon, "icon");
        }
    }

    public record NumericControl(double step, double minimum, double maximum) {
        public NumericControl {
            if (!Double.isFinite(step) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                    || step <= 0D || minimum > maximum) {
                throw new IllegalArgumentException("Numeric controls require a positive finite step and ordered finite bounds");
            }
        }
    }

    public record RootShortcut(TextKey name, TextKey description, Material icon, Consumer<Player> action) {
        public RootShortcut {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(action, "action");
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
        private final Consumer<Player> returnAction;
        private View view;
        private boolean busy;

        private Session(Player player, Consumer<Player> returnAction) {
            this.player = player;
            owner = player.getUniqueId();
            this.returnAction = returnAction;
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
        private List<RootShortcut> shortcuts;
        private List<Integer> contentSlots;
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
