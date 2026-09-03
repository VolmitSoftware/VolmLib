package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private static final int PREVIOUS = 47;
    private static final int SEARCH = 48;
    private static final int REFRESH = 49;
    private static final int CLEAR_SEARCH = 50;
    private static final int NEXT = 51;
    private static final int CLOSE = 53;
    private static final int MAXIMUM_INPUT_LENGTH = 512;
    private static final Method INVENTORY_VIEW_GET_TOP_INVENTORY = resolveInventoryViewTopInventory();
    private static final List<String> PLURAL_FORMS = List.of("zero", "one", "two", "few", "many", "other");

    private final Plugin plugin;
    private final Options options;
    private final PluginLanguageEditor editor;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PluginLanguageEditor.Document>> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    BukkitLanguageEditor(Plugin plugin, Options options) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.options = Objects.requireNonNull(options, "options");
        editor = new PluginLanguageEditor(options.languages(), options.switcher().editor());
    }

    void open(Player player, String locale) {
        FoliaScheduler.runEntity(plugin, player, () -> {
            if (!allowed(player)) {
                return;
            }
            cancel(player.getUniqueId());
            if (locale == null) {
                show(player, new View(null, null, "", 1, null));
            } else {
                load(player, new View(locale, null, "", 1, null));
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || holder.owner != this) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE || views.get(player.getUniqueId()) != holder.view) {
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> click(player, holder.view, slot), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder && holder.owner == this) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder && holder.owner == this
                && !prompts.containsKey(event.getPlayer().getUniqueId())) {
            views.remove(event.getPlayer().getUniqueId(), holder.view);
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
        FoliaScheduler.runEntity(plugin, player, () -> submit(player, prompt, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        closed = true;
        for (CompletableFuture<PluginLanguageEditor.Document> future : pending.values()) {
            future.cancel(true);
        }
        pending.clear();
        prompts.clear();
        views.clear();
        editor.close();
        HandlerList.unregisterAll(this);
        Plugin schedulerOwner = schedulerOwner();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Runnable cleanup = () -> {
                if (inventoryViewTopInventory(player.getOpenInventory()).getHolder() instanceof Holder holder
                        && holder.owner == this) {
                    player.closeInventory();
                }
            };
            if (FoliaScheduler.isOwnedByCurrentRegion(player)) {
                cleanup.run();
            } else if (schedulerOwner != null) {
                FoliaScheduler.runEntity(schedulerOwner, player, cleanup);
            }
        }
    }

    private void click(Player player, View view, int slot) {
        if (views.get(player.getUniqueId()) != view || !allowed(player)) {
            return;
        }
        if (slot == CLOSE) {
            cancel(player.getUniqueId());
            player.closeInventory();
        } else if (slot == BACK) {
            back(player, view);
        } else if (slot == PREVIOUS || slot == NEXT) {
            show(player, new View(view.locale(), view.document(), view.filter(),
                    view.page() + (slot == NEXT ? 1 : -1), view.key()));
        } else if (slot == REFRESH) {
            if (view.locale() == null) {
                show(player, view);
            } else {
                load(player, view);
            }
        } else if (slot == SEARCH && view.document() != null && view.key() == null) {
            beginPrompt(player, new Prompt(view, null, null, null));
        } else if (slot == CLEAR_SEARCH && !view.filter().isEmpty()) {
            show(player, new View(view.locale(), view.document(), "", 1, null));
        } else if (slot < PAGE_SIZE) {
            selectEntry(player, view, slot);
        }
    }

    private void back(Player player, View view) {
        if (view.key() != null) {
            show(player, new View(view.locale(), view.document(), view.filter(),
                    Math.max(0, keys(view).indexOf(view.key())) / PAGE_SIZE + 1, null));
        } else if (view.locale() != null) {
            show(player, new View(null, null, "", 1, null));
        } else {
            cancel(player.getUniqueId());
            player.closeInventory();
            options.back().accept(player);
        }
    }

    private void selectEntry(Player player, View view, int slot) {
        int index = (view.page() - 1) * PAGE_SIZE + slot;
        if (view.locale() == null) {
            List<String> locales = options.languages().availableLocales();
            if (index < locales.size()) {
                load(player, new View(locales.get(index), null, "", 1, null));
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
            show(player, new View(view.locale(), view.document(), view.filter(), 1, key));
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
        player.closeInventory();
        message(player, "Loading " + view.locale() + " messages...");
        complete(player, view, editor.load(view.locale()), false);
    }

    private void complete(Player player, View view, CompletableFuture<PluginLanguageEditor.Document> future, boolean saved) {
        UUID playerId = player.getUniqueId();
        CompletableFuture<PluginLanguageEditor.Document> previous = pending.put(playerId, future);
        if (previous != null) {
            previous.cancel(true);
        }
        future.whenComplete((document, failure) -> {
            if (closed) {
                return;
            }
            FoliaScheduler.runEntity(plugin, player, () -> {
                if (!pending.remove(playerId, future) || !player.isOnline() || !allowed(player)) {
                    return;
                }
                if (failure != null) {
                    failed(player, failure);
                    show(player, view.document() == null ? new View(null, null, "", 1, null) : view);
                    return;
                }
                if (saved) {
                    message(player, "Saved " + view.locale() + ". Language selections are unchanged.");
                }
                show(player, new View(document.locale(), document, view.filter(), view.page(), view.key()));
            });
        });
    }

    private void beginPrompt(Player player, Prompt prompt) {
        prompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        if (prompt.key() == null) {
            message(player, "Search message keys or text. Type cancel to return.");
        } else {
            message(player, "Edit " + prompt.view().locale() + ": " + prompt.key().id()
                    + (prompt.form() == null ? "" : " [" + partLabel(prompt.expected(), prompt.form()) + "]"));
            String current = rawValue(prompt.expected(), prompt.form()).replace("\n", "\\n");
            message(player, "Current: " + (current.length() > 512 ? current.substring(0, 512) + "..." : current));
            message(player, "Variables: " + variables(prompt.key()));
            message(player, "Enter text in chat; use \\n for new lines, \\\\ for a backslash, or cancel to return.");
        }
        FoliaScheduler.runEntity(plugin, player, () -> {
            if (prompts.remove(player.getUniqueId(), prompt) && allowed(player)) {
                message(player, "Language editor input expired.");
                show(player, prompt.view());
            }
        }, 1200L, () -> cancel(player.getUniqueId()));
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
            message(player, "Editor input must be at most " + MAXIMUM_INPUT_LENGTH + " characters.");
            show(player, prompt.view());
            return;
        }
        if (prompt.key() == null) {
            View view = prompt.view();
            show(player, new View(view.locale(), view.document(), input.strip(), 1, null));
            return;
        }
        try {
            MessageValue replacement = replacement(prompt.expected(), prompt.form(), decodeInput(input));
            complete(player, prompt.view(), editor.save(new PluginLanguageEditor.Edit(prompt.view().locale(),
                    prompt.key().id(), prompt.expected(), replacement)), true);
        } catch (IllegalArgumentException exception) {
            message(player, "Unable to save: " + exception.getMessage());
            show(player, prompt.view());
        }
    }

    private void show(Player player, View requested) {
        if (!allowed(player)) {
            return;
        }
        List<String> locales = requested.locale() == null ? options.languages().availableLocales() : List.of();
        List<MessageKey> keys = requested.document() != null && requested.key() == null ? keys(requested) : List.of();
        List<String> parts = requested.key() == null ? List.of() : parts(requested);
        int count = requested.locale() == null ? locales.size() : requested.key() == null ? keys.size() : parts.size();
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(count, requested.page(), PAGE_SIZE);
        View view = new View(requested.locale(), requested.document(), requested.filter(), page.page(), requested.key());
        Holder holder = new Holder(this, view);
        String title = plugin.getName() + " - " + (view.locale() == null ? "Language editor" : view.locale());
        Inventory inventory = plugin.getServer().createInventory(holder, SIZE, title);
        holder.inventory = inventory;
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        for (int index = page.startIndex(); index < page.endIndex(); index++) {
            ItemStack entry;
            if (view.locale() == null) {
                String locale = locales.get(index);
                entry = item(Material.BOOK, locale + " - " + VolmitLocales.displayName(locale).orElse(locale),
                        List.of("Edit this language's messages"));
            } else if (view.key() == null) {
                MessageKey key = keys.get(index);
                entry = messageItem(key, view.document().snapshot().value(key), null);
            } else {
                entry = messageItem(view.key(), view.document().snapshot().value(view.key()), parts.get(index));
            }
            inventory.setItem(index - page.startIndex(), entry);
        }
        inventory.setItem(BACK, item(Material.ARROW, "Back", List.of()));
        inventory.setItem(REFRESH, item(Material.CLOCK, "Refresh", List.of("Page " + page.page() + " of " + page.pages())));
        inventory.setItem(CLOSE, item(Material.BARRIER, "Close", List.of()));
        if (page.hasPrevious()) {
            inventory.setItem(PREVIOUS, item(Material.ARROW, "Previous page", List.of()));
        }
        if (page.hasNext()) {
            inventory.setItem(NEXT, item(Material.ARROW, "Next page", List.of()));
        }
        if (view.document() != null && view.key() == null) {
            inventory.setItem(SEARCH, item(Material.COMPASS, "Search messages", List.of(view.filter())));
            if (!view.filter().isEmpty()) {
                inventory.setItem(CLEAR_SEARCH, item(Material.PAPER, "Clear search", List.of()));
            }
        }
        views.put(player.getUniqueId(), view);
        player.openInventory(inventory);
    }

    private ItemStack messageItem(MessageKey key, MessageValue value, String form) {
        List<String> lore = new ArrayList<>();
        lore.add("Variables: " + variables(key));
        lore.addAll(preview(rawValue(value, form), 44, 6));
        lore.add(form == null && value instanceof PluralValue ? "Click to edit a plural form"
                : form == null && value instanceof LinesValue ? "Click to edit individual lines" : "Click to edit in chat");
        return item(Material.PAPER, form == null ? key.id() : partLabel(value, form), lore);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ComponentText.markup("<" + options.switcher().theme().primaryRight() + ">"
                    + DirectorMiniMenu.escapeText(name) + "</" + options.switcher().theme().primaryRight() + ">").legacy());
            List<String> styled = new ArrayList<>(lore.size());
            for (String line : lore) {
                styled.add(ChatColor.GRAY + line);
            }
            meta.setLore(styled);
            stack.setItemMeta(meta);
        }
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
        message(player, "You do not have permission to edit " + plugin.getName() + " languages.");
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
        message(player, "Unable to edit: " + Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName()));
    }

    private void message(Player player, String text) {
        ComponentMessenger.send(player, ComponentText.markup("<" + options.switcher().theme().description() + ">"
                + DirectorMiniMenu.escapeText(text) + "</" + options.switcher().theme().description() + ">"));
    }

    private Plugin schedulerOwner() {
        if (plugin.isEnabled()) {
            return plugin;
        }
        for (RegisteredServiceProvider<?> registration : plugin.getServer().getServicesManager().getRegistrations(Map.class)) {
            if (registration.getPlugin().isEnabled() && registration.getProvider() instanceof Map<?, ?> provider
                    && "2".equals(provider.get("volmit.language.protocol"))) {
                return registration.getPlugin();
            }
        }
        return null;
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

    private static String partLabel(MessageValue value, String part) {
        return value instanceof LinesValue ? "Line " + (Integer.parseInt(part) + 1) : part;
    }

    private static List<MessageKey> keys(View view) {
        String filter = view.filter().toLowerCase(Locale.ROOT);
        List<MessageKey> keys = new ArrayList<>();
        for (MessageKey key : view.document().snapshot().catalog().keys()) {
            if (filter.isEmpty() || key.id().toLowerCase(Locale.ROOT).contains(filter)
                    || rawValue(view.document().snapshot().value(key), null).toLowerCase(Locale.ROOT).contains(filter)) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.comparing(MessageKey::id));
        return keys;
    }

    static String variables(MessageKey key) {
        return key.placeholders().isEmpty() ? "None" : String.join(" ", key.placeholders().stream()
                .sorted().map(name -> "{" + name + "}").toList());
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
        String rendered = ComponentText.markup(text).legacy();
        if (rendered.isEmpty()) {
            return List.of("(empty)");
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

    record Options(PluginLanguageService languages, BukkitLanguageSwitcher.Options switcher, Consumer<Player> back) {
    }

    private record View(String locale, PluginLanguageEditor.Document document, String filter, int page, MessageKey key) {
    }

    private record Prompt(View view, MessageKey key, String form, MessageValue expected) {
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
