package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.inventorygui.BukkitInventoryShutdown;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.ChatColor;
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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

final class BukkitLanguageEditor implements AutoCloseable, Listener {
    static final int PAGE_SIZE = 45;
    private static final int SIZE = 54;
    private static final int BACK = 45;
    private static final int PREVIOUS = 48;
    private static final int SEARCH = 49;
    private static final int NEXT = 50;
    private static final int CLOSE = 53;
    private static final int MAXIMUM_INPUT_LENGTH = 512;
    private static final Method INVENTORY_VIEW_GET_TOP_INVENTORY = resolveInventoryViewTopInventory();
    private static final List<String> PLURAL_FORMS = List.of("zero", "one", "two", "few", "many", "other");

    private final Plugin plugin;
    private final Options options;
    private final PluginLanguageEditor editor;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitInventoryShutdown.View> openInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PluginLanguageEditor.Document>> pending = new ConcurrentHashMap<>();
    private volatile DirectorMiniMenu.Theme theme;
    private volatile boolean closed;

    BukkitLanguageEditor(Plugin plugin, Options options) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.options = Objects.requireNonNull(options, "options");
        theme = options.switcher().theme();
        editor = new PluginLanguageEditor(options.languages(), options.switcher().editor());
    }

    void updateTheme(DirectorMiniMenu.Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    void open(Player player, String locale) {
        open(player, locale, options.back());
    }

    void open(Player player, String locale, Consumer<Player> back) {
        Consumer<Player> destination = Objects.requireNonNull(back, "back");
        Runnable opening = () -> {
            if (!allowed(player)) {
                return;
            }
            cancel(player.getUniqueId());
            if (locale == null) {
                show(player, new View(null, null, null, "", 1, null, destination));
            } else {
                load(player, new View(locale, null, null, "", 1, null, destination));
            }
        };
        if (FoliaScheduler.isOwnedByCurrentRegion(player)) {
            opening.run();
        } else {
            FoliaScheduler.runEntity(plugin, player, opening);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(inventoryViewTopInventory(event.getView()).getHolder() instanceof Holder holder)
                || holder.owner != this) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        views.put(player.getUniqueId(), holder.view);
        Runnable routing = () -> {
            if (inventoryViewTopInventory(player.getOpenInventory()).getHolder() != holder) {
                return;
            }
            views.put(player.getUniqueId(), holder.view);
            click(player, holder.view, slot);
        };
        if (!FoliaScheduler.runEntity(plugin, player, routing, 1L)
                && FoliaScheduler.isOwnedByCurrentRegion(player)) {
            routing.run();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (inventoryViewTopInventory(event.getView()).getHolder() instanceof Holder holder
                && holder.owner == this) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        BukkitInventoryShutdown.View open = openInventories.get(playerId);
        if (open != null && open.inventory() == event.getInventory()) {
            openInventories.remove(playerId, open);
        }
        if (event.getInventory().getHolder() instanceof Holder holder && holder.owner == this
                && !prompts.containsKey(playerId)) {
            if (pending.containsKey(playerId)) {
                cancel(playerId);
            } else {
                views.remove(playerId, holder.view);
            }
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || holder.owner != this) {
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
        UUID playerId = player.getUniqueId();
        Runnable retired = () -> views.remove(playerId, prompt.view());
        if (!FoliaScheduler.runEntity(plugin, player, () -> submit(player, prompt, input), 0L, retired)) {
            retired.run();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
        cancel(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (CompletableFuture<PluginLanguageEditor.Document> future : pending.values()) {
            future.cancel(true);
        }
        pending.clear();
        prompts.clear();
        editor.close();
        BukkitInventoryShutdown.drain(plugin, openInventories.values());
        openInventories.clear();
        views.clear();
        HandlerList.unregisterAll(this);
    }

    private void click(Player player, View view, int slot) {
        if (!allowed(player)) {
            return;
        }
        if (slot == CLOSE) {
            cancel(player.getUniqueId());
            player.closeInventory();
        } else if (slot == BACK) {
            back(player, view);
        } else if (slot == PREVIOUS || slot == NEXT) {
            show(player, new View(view.locale(), view.document(), view.group(), view.filter(),
                    view.page() + (slot == NEXT ? 1 : -1), view.key(), view.back()));
        } else if (slot == SEARCH && view.document() != null && view.group() == null
                && view.key() == null && view.filter().isEmpty()) {
            beginPrompt(player, new Prompt(view, null, null, null));
        } else if (slot == SEARCH && !view.filter().isEmpty()) {
            show(player, new View(view.locale(), view.document(), view.group(), "", 1, null, view.back()));
        } else if (slot < PAGE_SIZE) {
            selectEntry(player, view, slot);
        }
    }

    private void back(Player player, View view) {
        if (view.key() != null) {
            show(player, new View(view.locale(), view.document(), view.group(), view.filter(),
                    Math.max(0, keys(view).indexOf(view.key())) / PAGE_SIZE + 1, null, view.back()));
        } else if (view.group() != null) {
            show(player, new View(view.locale(), view.document(), null, "", 1, null, view.back()));
        } else if (!view.filter().isEmpty()) {
            show(player, new View(view.locale(), view.document(), null, "", 1, null, view.back()));
        } else if (view.locale() != null) {
            show(player, new View(null, null, null, "", 1, null, view.back()));
        } else {
            cancel(player.getUniqueId());
            player.closeInventory();
            view.back().accept(player);
        }
    }

    private void selectEntry(Player player, View view, int slot) {
        int ordinal = contentOrdinal(view, slot);
        if (ordinal < 0) {
            return;
        }
        int index = (view.page() - 1) * pageSize(view) + ordinal;
        if (view.locale() == null) {
            List<String> locales = options.languages().availableLocales();
            if (index < locales.size()) {
                load(player, new View(locales.get(index), null, null, "", 1, null, view.back()));
            }
            return;
        }
        if (view.group() == null && view.filter().isEmpty()) {
            List<String> groups = groups(view.document());
            if (index < groups.size()) {
                show(player, new View(view.locale(), view.document(), groups.get(index), "", 1, null, view.back()));
            }
            return;
        }
        if (view.key() != null) {
            List<String> forms = parts(view);
            if (index < forms.size()) {
                beginPrompt(player, new Prompt(view, view.key(), forms.get(index),
                        view.document().snapshot().value(view.key())));
            }
            return;
        }
        List<MessageKey> keys = keys(view);
        if (index >= keys.size()) {
            return;
        }
        MessageKey key = keys.get(index);
        MessageValue value = view.document().snapshot().value(key);
        if (value instanceof PluralValue || value instanceof LinesValue) {
            show(player, new View(view.locale(), view.document(), view.group(), view.filter(), 1, key, view.back()));
        } else {
            beginPrompt(player, new Prompt(view, key, null, value));
        }
    }

    private void load(Player player, View view) {
        if (!allowed(player)) {
            return;
        }
        views.remove(player.getUniqueId());
        prompts.remove(player.getUniqueId());
        message(
                player,
                BukkitLanguageMessages.EDITOR_LOADING,
                MessageArgument.untrusted("locale", view.locale())
        );
        complete(player, view, editor.load(view.locale()), null);
    }

    private void complete(Player player, View view, CompletableFuture<PluginLanguageEditor.Document> future,
                          Change change) {
        UUID playerId = player.getUniqueId();
        CompletableFuture<PluginLanguageEditor.Document> previous = pending.put(playerId, future);
        if (previous != null) {
            previous.cancel(true);
        }
        future.whenComplete((document, failure) -> {
            if (closed) {
                return;
            }
            Runnable retired = () -> {
                if (pending.remove(playerId, future)) {
                    views.remove(playerId, view);
                }
            };
            if (!FoliaScheduler.runEntity(plugin, player, () -> {
                if (!pending.remove(playerId, future) || !player.isOnline() || !allowed(player)) {
                    return;
                }
                if (failure != null) {
                    failed(player, failure);
                    show(player, view.document() == null
                            ? new View(null, null, null, "", 1, null, view.back()) : view);
                    return;
                }
                if (change != null) {
                    saved(player, view, change);
                }
                show(player, new View(document.locale(), document, view.group(), view.filter(),
                        view.page(), view.key(), view.back()));
            }, 0L, retired)) {
                retired.run();
            }
        });
    }

    private void beginPrompt(Player player, Prompt prompt) {
        prompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        if (prompt.key() == null) {
            promptMenu(player, prompt.view(), List.of(localized(
                    player,
                    BukkitLanguageMessages.EDITOR_SEARCH_PROMPT
            )));
        } else {
            String key = prompt.key().id()
                    + (prompt.form() == null ? "" : " [" + partLabel(player, prompt.expected(), prompt.form()) + "]");
            String current = clip(rawValue(prompt.expected(), prompt.form()).replace("\n", "\\n"));
            promptMenu(player, prompt.view(), List.of(
                    localized(
                            player,
                            BukkitLanguageMessages.EDITOR_VALUE_PROMPT,
                            MessageArgument.untrusted("key", key)
                    ),
                    localized(
                            player,
                            BukkitLanguageMessages.EDITOR_CURRENT_VALUE,
                            MessageArgument.trusted("value", current)
                    ),
                    localized(
                            player,
                            BukkitLanguageMessages.EDITOR_VARIABLES,
                            MessageArgument.untrusted("variables", variables(player, prompt.key()))
                    ),
                    localized(player, BukkitLanguageMessages.EDITOR_INPUT_GUIDANCE)
            ));
        }
        Runnable retired = () -> cancel(player.getUniqueId());
        if (!FoliaScheduler.runEntity(plugin, player, () -> {
            if (prompts.remove(player.getUniqueId(), prompt) && allowed(player)) {
                message(player, BukkitLanguageMessages.EDITOR_INPUT_EXPIRED);
                show(player, prompt.view());
            }
        }, 1200L, retired)) {
            retired.run();
        }
    }

    private void submit(Player player, Prompt prompt, String input) {
        if (views.get(player.getUniqueId()) != prompt.view() || !allowed(player)) {
            return;
        }
        if (input.equalsIgnoreCase("cancel")) {
            show(player, prompt.view());
            return;
        }
        if (input.length() > MAXIMUM_INPUT_LENGTH) {
            message(
                    player,
                    BukkitLanguageMessages.EDITOR_INPUT_TOO_LONG,
                    MessageArgument.trusted("maximum", MAXIMUM_INPUT_LENGTH)
            );
            show(player, prompt.view());
            return;
        }
        if (prompt.key() == null) {
            View view = prompt.view();
            show(player, new View(view.locale(), view.document(), view.group(), input.strip(), 1, null, view.back()));
            return;
        }
        try {
            MessageValue replacement = replacement(prompt.expected(), prompt.form(), decodeInput(input));
            Change change = new Change(prompt.key().id(),
                    rawValue(prompt.expected(), prompt.form()), rawValue(replacement, prompt.form()));
            complete(player, prompt.view(), editor.save(new PluginLanguageEditor.Edit(prompt.view().locale(),
                    prompt.key().id(), prompt.expected(), replacement)), change);
        } catch (IllegalArgumentException exception) {
            message(
                    player,
                    BukkitLanguageMessages.EDITOR_UNABLE_TO_SAVE,
                    MessageArgument.untrusted(
                            "reason",
                            Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName())
                    )
            );
            show(player, prompt.view());
        }
    }

    private void show(Player player, View requested) {
        if (!allowed(player)) {
            return;
        }
        List<String> locales = requested.locale() == null ? options.languages().availableLocales() : List.of();
        boolean categoryView = requested.document() != null && requested.group() == null
                && requested.filter().isEmpty();
        List<String> groups = categoryView
                ? groups(requested.document()) : List.of();
        List<MessageKey> keys = requested.document() != null && !categoryView && requested.key() == null
                ? keys(requested) : List.of();
        List<String> parts = requested.key() == null ? List.of() : parts(requested);
        int count = requested.locale() == null ? locales.size()
                : categoryView ? groups.size()
                : requested.key() == null ? keys.size() : parts.size();
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(count, requested.page(), pageSize(requested));
        View view = new View(requested.locale(), requested.document(), requested.group(), requested.filter(),
                page.page(), requested.key(), requested.back());
        Holder holder = new Holder(this, view);
        String section;
        if (view.locale() == null) {
            section = localized(player, BukkitLanguageMessages.EDITOR_LANGUAGES).plain();
        } else if (view.group() != null) {
            section = localized(
                    player,
                    BukkitLanguageMessages.EDITOR_SECTION_GROUP,
                    MessageArgument.untrusted("locale", view.locale()),
                    MessageArgument.untrusted("group", groupName(view.group()))
            ).plain();
        } else if (!view.filter().isEmpty()) {
            section = localized(
                    player,
                    BukkitLanguageMessages.EDITOR_SECTION_SEARCH,
                    MessageArgument.untrusted("locale", view.locale())
            ).plain();
        } else {
            section = view.locale();
        }
        DirectorMiniMenu.Theme theme = this.theme;
        String title = ComponentText.literal(plugin.getName() + " › " + section).legacy();
        Inventory inventory = plugin.getServer().createInventory(holder, SIZE, title);
        holder.inventory = inventory;
        ItemStack filler = formattedItem(Material.BLACK_STAINED_GLASS_PANE, ComponentText.literal(" "), List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        List<Integer> contentSlots = contentSlots(view, page.endIndex() - page.startIndex());
        for (int index = page.startIndex(); index < page.endIndex(); index++) {
            ItemStack entry;
            if (view.locale() == null) {
                String locale = locales.get(index);
                boolean active = locale.equalsIgnoreCase(options.languages().defaultLocale());
                entry = localeItem(player, locale, VolmitLocales.displayName(locale).orElse(locale), active);
            } else if (view.group() == null && view.filter().isEmpty()) {
                String group = groups.get(index);
                entry = categoryItem(player, group, groupName(group),
                        messageCount(view.document(), group));
            } else if (view.key() == null) {
                MessageKey key = keys.get(index);
                entry = messageItem(player, key, view.document().snapshot().value(key), null);
            } else {
                entry = messageItem(player, view.key(), view.document().snapshot().value(view.key()), parts.get(index));
            }
            inventory.setItem(contentSlots.get(index - page.startIndex()), entry);
        }
        inventory.setItem(BACK, formattedItem(
                Material.ARROW,
                localized(player, BukkitLanguageMessages.EDITOR_BACK),
                List.of()
        ));
        inventory.setItem(CLOSE, formattedItem(
                Material.BARRIER,
                localized(player, BukkitLanguageMessages.EDITOR_CLOSE),
                List.of()
        ));
        if (page.hasPrevious()) {
            inventory.setItem(PREVIOUS, formattedItem(
                    Material.ARROW,
                    localized(player, BukkitLanguageMessages.EDITOR_PREVIOUS_PAGE),
                    List.of()
            ));
        }
        if (page.hasNext()) {
            inventory.setItem(NEXT, formattedItem(
                    Material.ARROW,
                    localized(player, BukkitLanguageMessages.EDITOR_NEXT_PAGE),
                    List.of()
            ));
        }
        if (view.document() != null && view.group() == null && view.key() == null && view.filter().isEmpty()) {
            inventory.setItem(SEARCH, formattedItem(
                    Material.COMPASS,
                    themed(player, BukkitLanguageMessages.EDITOR_SEARCH_MESSAGES, theme.primaryRight()),
                    List.of(themed(
                            player,
                            BukkitLanguageMessages.EDITOR_SEARCH_MESSAGES_LORE,
                            theme.description()
                    ))
            ));
        }
        if (view.document() != null && view.group() == null && view.key() == null && !view.filter().isEmpty()) {
            inventory.setItem(SEARCH, formattedItem(
                    Material.PAPER,
                    themed(player, BukkitLanguageMessages.EDITOR_CLEAR_SEARCH, theme.primaryRight()),
                    List.of()
            ));
        }
        views.put(player.getUniqueId(), view);
        player.openInventory(inventory);
        if (inventoryViewTopInventory(player.getOpenInventory()) == inventory) {
            BukkitInventoryShutdown.View opened = new BukkitInventoryShutdown.View(player, inventory);
            openInventories.put(player.getUniqueId(), opened);
            if (closed) {
                player.closeInventory();
                openInventories.remove(player.getUniqueId(), opened);
            }
        }
    }

    private ItemStack messageItem(Player player, MessageKey key, MessageValue value, String form) {
        List<ComponentText> lore = new ArrayList<>();
        lore.add(localized(player, BukkitLanguageMessages.EDITOR_CURRENT_VALUE_LABEL));
        for (String line : preview(player, rawValue(value, form), 44, 6)) {
            lore.add(ComponentText.section(line));
        }
        if (!key.placeholders().isEmpty()) {
            lore.add(ComponentText.empty());
            lore.add(localized(
                    player,
                    BukkitLanguageMessages.EDITOR_VARIABLE_LIST,
                    MessageArgument.untrusted("variables", variables(player, key))
            ));
        }
        TextKey instruction = form == null && value instanceof PluralValue
                ? BukkitLanguageMessages.EDITOR_EDIT_PLURAL
                : form == null && value instanceof LinesValue
                ? BukkitLanguageMessages.EDITOR_EDIT_LINES
                : BukkitLanguageMessages.EDITOR_EDIT_CHAT;
        lore.add(localized(player, instruction));
        return formattedItem(options.presentation().icon(key.id(), Material.PAPER),
                ComponentText.markup("&f" + DirectorMiniMenu.escapeText(
                        form == null ? key.id() : partLabel(player, value, form)) + "&r"), lore);
    }

    private ItemStack localeItem(Player player, String locale, String name, boolean active) {
        return formattedItem(active ? Material.WRITABLE_BOOK : Material.BOOK,
                localeTitle(locale, name, active),
                List.of(localized(player, BukkitLanguageMessages.EDITOR_OPEN_LANGUAGE)));
    }

    static ComponentText localeTitle(String locale, String name, boolean active) {
        return ComponentText.markup(
                (active ? "<green>✔</green> " : "<dark_gray>•</dark_gray> ")
                        + "<white>" + DirectorMiniMenu.escapeText(locale) + "</white>"
                        + " <dark_gray>—</dark_gray> "
                        + "<gray>" + DirectorMiniMenu.escapeText(name) + "</gray>"
        );
    }

    private ItemStack categoryItem(Player player, String group, String name, int messages) {
        return formattedItem(options.presentation().icon(group, groupMaterial(group)),
                categoryTitle(name),
                List.of(
                        localized(
                                player,
                                BukkitLanguageMessages.EDITOR_MESSAGE_COUNT,
                                MessageArgument.trusted("count", messages)
                        ),
                        localized(player, BukkitLanguageMessages.EDITOR_OPEN_CATEGORY)
                ));
    }

    static ComponentText categoryTitle(String name) {
        return ComponentText.markup("&d" + DirectorMiniMenu.escapeText(name) + "&r");
    }

    static Set<Integer> navigationSlots() {
        return Set.of(BACK, PREVIOUS, SEARCH, NEXT, CLOSE);
    }

    static List<Integer> categorySlots(int count) {
        return categorySlots(count, BukkitLanguageEditorPresentation.Layout.CENTERED);
    }

    static List<Integer> categorySlots(int count, BukkitLanguageEditorPresentation.Layout layout) {
        int bounded = Math.max(0, Math.min(count, layout.categoryPageSize()));
        if (bounded == 0) {
            return List.of();
        }
        if (layout == BukkitLanguageEditorPresentation.Layout.FOUR_ROWS) {
            ArrayList<Integer> slots = new ArrayList<>(bounded);
            for (int slot = 0; slot < bounded; slot++) {
                slots.add(slot);
            }
            return List.copyOf(slots);
        }
        int rows = (bounded + 6) / 7;
        int firstRow = (5 - rows) / 2;
        ArrayList<Integer> slots = new ArrayList<>(bounded);
        int remaining = bounded;
        for (int row = 0; row < rows; row++) {
            int rowsLeft = rows - row;
            int rowSize = (remaining + rowsLeft - 1) / rowsLeft;
            int firstColumn = (9 - rowSize) / 2;
            for (int column = firstColumn; column < firstColumn + rowSize; column++) {
                slots.add((firstRow + row) * 9 + column);
            }
            remaining -= rowSize;
        }
        return List.copyOf(slots);
    }

    private int pageSize(View view) {
        return view.locale() != null && view.group() == null && view.filter().isEmpty()
                ? options.presentation().layout().categoryPageSize()
                : options.presentation().layout().messagePageSize();
    }

    private List<Integer> contentSlots(View view, int count) {
        if (view.locale() != null && view.group() == null && view.filter().isEmpty()) {
            return categorySlots(count, options.presentation().layout());
        }
        ArrayList<Integer> slots = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    private int contentOrdinal(View view, int slot) {
        if (view.locale() != null && view.group() == null && view.filter().isEmpty()) {
            DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(
                    groups(view.document()).size(), view.page(), pageSize(view));
            return categorySlots(page.endIndex() - page.startIndex(), options.presentation().layout()).indexOf(slot);
        }
        return slot < pageSize(view) ? slot : -1;
    }

    private ItemStack formattedItem(Material material, ComponentText name, List<ComponentText> lore) {
        return formattedItem(new ItemStack(material), name, lore);
    }

    private ItemStack formattedItem(ItemStack stack, ComponentText name, List<ComponentText> lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(name.legacy());
        meta.setLore(lore.stream().map(ComponentText::legacy).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean allowed(Player player) {
        if (closed || !plugin.isEnabled()) {
            return false;
        }
        CommandSender sender = player;
        if (sender.hasPermission("volmit.language.admin") || sender.hasPermission(options.switcher().adminPermission())) {
            return true;
        }
        cancel(player.getUniqueId());
        message(
                player,
                BukkitLanguageMessages.EDITOR_NO_PERMISSION,
                MessageArgument.untrusted("plugin", plugin.getName())
        );
        player.closeInventory();
        return false;
    }

    private void cancel(UUID playerId) {
        prompts.remove(playerId);
        views.remove(playerId);
        CompletableFuture<PluginLanguageEditor.Document> future = pending.remove(playerId);
        if (future != null) {
            future.cancel(true);
        }
    }

    private void failed(Player player, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        plugin.getLogger().log(Level.WARNING, "Unable to edit " + plugin.getName() + " language messages", cause);
        message(
                player,
                BukkitLanguageMessages.EDITOR_UNABLE_TO_EDIT,
                MessageArgument.untrusted(
                        "reason",
                        Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName())
                )
        );
    }

    private void message(Player player, TextKey key, MessageArgument... arguments) {
        ComponentMessenger.send(player, themed(player, key, theme.description(), arguments));
    }

    private ComponentText themed(Player player,
                                 TextKey key,
                                 String color,
                                 MessageArgument... arguments) {
        ComponentText content = localized(player, key, arguments);
        return ComponentText.markup("<" + color + ">" + content.miniMessage() + "</" + color + ">");
    }

    ComponentText localized(Player player, TextKey key, MessageArgument... arguments) {
        MessageArgs.Builder builder = MessageArgs.builder();
        if (arguments != null) {
            for (MessageArgument argument : arguments) {
                builder.add(argument);
            }
        }
        MessageArgs supplied = builder.build();
        String template;
        MessageArgs resolvedArguments;
        try {
            ResolvedText resolved = options.languages().snapshot(player.getUniqueId()).resolve(key, supplied);
            template = resolved.template();
            resolvedArguments = resolved.arguments();
        } catch (RuntimeException failure) {
            template = key.english();
            resolvedArguments = supplied;
        }
        for (MessageArgument argument : resolvedArguments.arguments().values()) {
            String value = String.valueOf(argument.value());
            if (argument.kind() == MessageArgumentKind.UNTRUSTED) {
                value = DirectorMiniMenu.escapeText(value);
            }
            template = template.replace("{" + argument.name() + "}", value);
        }
        return ComponentText.markup(template);
    }

    private static Method resolveInventoryViewTopInventory() {
        try {
            return InventoryView.class.getMethod("getTopInventory");
        } catch (NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static Inventory inventoryViewTopInventory(InventoryView view) {
        try {
            return (Inventory) INVENTORY_VIEW_GET_TOP_INVENTORY.invoke(view);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to invoke InventoryView.getTopInventory", exception);
        }
    }

    private static List<String> parts(View view) {
        MessageValue value = view.document().snapshot().value(view.key());
        if (value instanceof LinesValue lines) {
            List<String> indices = new ArrayList<>(lines.lines().size());
            for (int index = 0; index < lines.lines().size(); index++) {
                indices.add(Integer.toString(index));
            }
            return indices;
        }
        PluralValue plural = (PluralValue) value;
        return PLURAL_FORMS.stream().filter(plural.forms()::containsKey).toList();
    }

    private String partLabel(Player player, MessageValue value, String part) {
        if (!(value instanceof LinesValue)) {
            return part;
        }
        return localized(
                player,
                BukkitLanguageMessages.EDITOR_LINE,
                MessageArgument.trusted("line", Integer.parseInt(part) + 1)
        ).plain();
    }

    private static List<MessageKey> keys(View view) {
        return matchingKeys(view.document(), view.group(), view.filter());
    }

    static List<MessageKey> matchingKeys(PluginLanguageEditor.Document document, String group, String filter) {
        String query = filter.toLowerCase(Locale.ROOT);
        List<MessageKey> keys = new ArrayList<>();
        for (MessageKey key : document.snapshot().catalog().keys()) {
            if (group != null && !group(key.id()).equals(group)) {
                continue;
            }
            if (query.isEmpty() || key.id().toLowerCase(Locale.ROOT).contains(query)
                    || rawValue(document.snapshot().value(key), null).toLowerCase(Locale.ROOT).contains(query)) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.comparing(MessageKey::id));
        return keys;
    }

    private void promptMenu(Player player, View view, List<ComponentText> content) {
        ArrayList<String> entries = new ArrayList<>(content.size());
        for (ComponentText line : content) {
            entries.add(entry(line));
        }
        String command = "/" + options.switcher().command() + " language server edit " + view.locale();
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                command, "/" + options.switcher().command() + " language server edit",
                command, entries, "", 1, Math.max(1, entries.size()));
        DirectorMiniMenu.deliverContent(player, menu, theme, options.switcher().textResolver());
    }

    private void saved(Player player, View view, Change change) {
        BukkitLanguageSwitcher.LanguageEditFeedback feedback = options.switcher().editorFeedback();
        if (feedback != null) {
            try {
                BukkitLanguageSwitcher.LanguageEditChange edit = new BukkitLanguageSwitcher.LanguageEditChange(
                        view.locale(), change.key(), clip(change.before()), clip(change.after()));
                ComponentText message = Objects.requireNonNull(
                        feedback.saved(player, edit), "Language edit feedback cannot be null");
                ComponentMessenger.send(player, message);
                return;
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Unable to render compact language edit feedback", exception);
            }
        }
        ComponentText changed = localized(
                player,
                BukkitLanguageMessages.EDITOR_CHANGED,
                MessageArgument.untrusted("key", change.key()),
                MessageArgument.trusted("before", clip(change.before())),
                MessageArgument.trusted("after", clip(change.after()))
        );
        promptMenu(player, view, List.of(
                localized(
                        player,
                        BukkitLanguageMessages.EDITOR_SAVED,
                        MessageArgument.untrusted("locale", view.locale())
                ),
                changed
        ));
    }

    private String entry(ComponentText content) {
        DirectorMiniMenu.Theme theme = this.theme;
        return "<" + theme.muted() + ">⇀ </" + theme.muted() + ">" + content.miniMessage();
    }

    private static String clip(String value) {
        return value.length() <= 512 ? value : value.substring(0, 512) + "...";
    }

    static List<String> groups(PluginLanguageEditor.Document document) {
        Set<String> groups = new LinkedHashSet<>();
        for (MessageKey key : document.snapshot().catalog().keys()) {
            groups.add(group(key.id()));
        }
        ArrayList<String> sorted = new ArrayList<>(groups);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(sorted);
    }

    static String group(String messageId) {
        int separator = messageId.indexOf('.');
        return separator < 0 ? messageId : messageId.substring(0, separator);
    }

    static String groupName(String group) {
        if (group.equalsIgnoreCase("gui") || group.equalsIgnoreCase("hud")
                || group.equalsIgnoreCase("api")) {
            return group.toUpperCase(Locale.ROOT);
        }
        String normalized = group.replace('_', ' ').replace('-', ' ').trim();
        return normalized.isEmpty() ? group
                : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static int messageCount(PluginLanguageEditor.Document document, String group) {
        int count = 0;
        for (MessageKey key : document.snapshot().catalog().keys()) {
            if (group(key.id()).equals(group)) {
                count++;
            }
        }
        return count;
    }

    private static Material groupMaterial(String group) {
        return switch (group.toLowerCase(Locale.ROOT)) {
            case "command" -> Material.COMMAND_BLOCK;
            case "config", "configuration" -> Material.COMPARATOR;
            case "debug", "diagnostics" -> Material.SPYGLASS;
            case "director", "help" -> Material.WRITABLE_BOOK;
            case "gui", "menu" -> Material.CHEST;
            case "hud", "presentation" -> Material.NAME_TAG;
            case "integration" -> Material.ENDER_CHEST;
            case "portal" -> Material.OBSIDIAN;
            case "runtime", "system" -> Material.REDSTONE_TORCH;
            default -> Material.PAPER;
        };
    }

    private String variables(Player player, MessageKey key) {
        String names = variableNames(key);
        if (names.isEmpty()) {
            return localized(player, BukkitLanguageMessages.EDITOR_NONE).plain();
        }
        return names;
    }

    static String variableNames(MessageKey key) {
        return String.join(" ", key.placeholders().stream().sorted().map(name -> "{" + name + "}").toList());
    }

    static MessageValue replacement(MessageValue current, String form, String value) {
        if (current instanceof TextValue) {
            return new TextValue(value);
        }
        if (current instanceof LinesValue lines) {
            List<String> updated = new ArrayList<>(lines.lines());
            updated.set(Integer.parseInt(Objects.requireNonNull(form, "line")), value);
            return new LinesValue(updated);
        }
        PluralValue plural = (PluralValue) current;
        Map<String, String> forms = new LinkedHashMap<>(plural.forms());
        forms.put(Objects.requireNonNull(form, "plural form"), value);
        return new PluralValue(forms);
    }

    static String rawValue(MessageValue value, String form) {
        if (value instanceof TextValue text) {
            return text.template();
        }
        if (value instanceof LinesValue lines) {
            return form == null ? String.join("\n", lines.lines()) : lines.lines().get(Integer.parseInt(form));
        }
        PluralValue plural = (PluralValue) value;
        if (form != null) {
            return plural.forms().getOrDefault(form, plural.forms().get("other"));
        }
        return String.join("\n", plural.forms().entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).toList());
    }

    static String decodeInput(String input) {
        StringBuilder result = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '\\' && index + 1 < input.length()) {
                char next = input.charAt(index + 1);
                if (next == 'n' || next == '\\') {
                    result.append(next == 'n' ? '\n' : '\\');
                    index++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    static List<String> preview(String text, int width, int maximumLines) {
        return preview(text, width, maximumLines, "(empty)");
    }

    private List<String> preview(Player player, String text, int width, int maximumLines) {
        return preview(
                text,
                width,
                maximumLines,
                localized(player, BukkitLanguageMessages.EDITOR_EMPTY).legacy()
        );
    }

    private static List<String> preview(String text, int width, int maximumLines, String empty) {
        String rendered = ComponentText.markup(text).legacy();
        if (rendered.isEmpty()) {
            return List.of(empty);
        }
        int safeWidth = Math.max(1, width);
        int safeMaximumLines = Math.max(1, maximumLines);
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int visible = 0;
        for (int index = 0; index < rendered.length(); index++) {
            char character = rendered.charAt(index);
            if (character == '§' && index + 1 < rendered.length()) {
                current.append(character).append(rendered.charAt(++index));
                continue;
            }
            if (character == '\r') {
                continue;
            }
            if (character == '\n' || visible == safeWidth) {
                lines.add(current.toString());
                if (lines.size() == safeMaximumLines) {
                    lines.set(safeMaximumLines - 1, lines.get(safeMaximumLines - 1) + "§8...");
                    return List.copyOf(lines);
                }
                current = new StringBuilder(ChatColor.getLastColors(current.toString()));
                visible = 0;
                if (character == '\n') {
                    continue;
                }
            }
            current.append(character);
            visible++;
        }
        if (visible > 0 || lines.isEmpty()) {
            lines.add(current.toString());
        }
        return List.copyOf(lines);
    }

    record Options(PluginLanguageService languages, BukkitLanguageSwitcher.Options switcher, Consumer<Player> back,
                   BukkitLanguageEditorPresentation presentation) {
        Options {
            Objects.requireNonNull(presentation, "presentation");
        }
    }

    private record View(String locale, PluginLanguageEditor.Document document, String group, String filter,
                        int page, MessageKey key, Consumer<Player> back) {
    }

    private record Prompt(View view, MessageKey key, String form, MessageValue expected) {
    }

    private record Change(String key, String before, String after) {
    }

    private static final class Holder implements InventoryHolder {
        private final BukkitLanguageEditor owner;
        private final View view;
        private Inventory inventory;

        private Holder(BukkitLanguageEditor owner, View view) {
            this.owner = owner;
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
