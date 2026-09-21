package art.arcane.volmlib.util.config;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.doAnswer;

public class BukkitConfigEditorTest {
    @Test
    public void customFeedbackWaitsForPersistedValuesAndDoesNotConfirmFailedWrites() throws Exception {
        verifySaveFeedback(true);
    }

    @Test
    public void defaultFeedbackStillReportsProgressAndSavedPath() throws Exception {
        verifySaveFeedback(false);
    }

    private void verifySaveFeedback(boolean customized) throws Exception {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(CommandSender.class, Entity.class));
        Entity entity = (Entity) player;
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        ExecutorService worker = mock(ExecutorService.class);
        AtomicReference<Runnable> pending = new AtomicReference<>();
        List<BukkitConfigEditor.Change> changes = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        List<TextKey> customMessages = new ArrayList<>();
        ConfigEditorDocument original = ConfigEditorDocument.fromToml("volume = 0.7");
        ConfigEditorDocument persisted = ConfigEditorDocument.fromToml("volume = 1.0");
        AtomicReference<Boolean> fail = new AtomicReference<>(false);
        when(plugin.getName()).thenReturn("Example");
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.createInventory(any(InventoryHolder.class), eq(54), anyString())).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(((CommandSender) player).hasPermission("example.config")).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(player.openInventory(inventory)).thenReturn(view);
        doAnswer(invocation -> {
            pending.set(invocation.getArgument(0));
            return null;
        }).when(worker).execute(any(Runnable.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Executors> executors = mockStatic(Executors.class);
             MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
             MockedStatic<ComponentMessenger> messenger = mockStatic(ComponentMessenger.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(mock(ItemFactory.class));
            executors.when(() -> Executors.newSingleThreadExecutor(any(ThreadFactory.class))).thenReturn(worker);
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(entity), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(2)).run();
                        return true;
                    });
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(entity), any(Runnable.class), eq(0L), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(2)).run();
                        return true;
                    });
            messenger.when(() -> ComponentMessenger.send(eq((CommandSender) player), any(ComponentText.class)))
                    .thenAnswer(invocation -> {
                        messages.add(((ComponentText) invocation.getArgument(1)).plain());
                        return null;
                    });
            try (BukkitConfigEditor editor = BukkitConfigEditor.register(plugin, new BukkitConfigEditor.Options(
                    () -> original, edit -> {
                        if (fail.get()) {
                            throw new IOException("File changed externally");
                        }
                        return persisted;
                    }, new BukkitConfigEditor.Presentation("example.config", DirectorMiniMenu.Theme.irisGreen(),
                    DirectorTextResolver.ENGLISH)))) {
                if (customized) {
                    editor.configureFeedback(new BukkitConfigEditor.Feedback(false, (sender, change) -> {
                        changes.add(change);
                        return ComponentText.literal("Example > " + change.setting() + ": " + change.before() + " -> " + change.after());
                    }, (sender, key, arguments) -> {
                        customMessages.add(key);
                        return ComponentText.literal("Example > " + DirectorTextResolver.ENGLISH.resolve(key, arguments));
                    }));
                }
                editor.apply(player, original.edit(List.of("volume"), new JsonPrimitive(2.0)), null);
                assertTrue(changes.isEmpty());
                assertEquals(customized ? List.of() : List.of("Saving configuration..."), messages);
                pending.getAndSet(null).run();
                if (customized) {
                    assertEquals(List.of(new BukkitConfigEditor.Change("volume", "0.7", "1.0")), changes);
                    assertEquals(List.of("Example > volume: 0.7 -> 1.0"), messages);
                    assertTrue(customMessages.isEmpty());
                } else {
                    assertEquals(List.of("Saving configuration...", "Saved volume. Changes apply automatically."), messages);
                }
                messages.clear();
                fail.set(true);
                editor.apply(player, original.edit(List.of("volume"), new JsonPrimitive(2.0)), null);
                pending.getAndSet(null).run();
                assertEquals(customized ? 1 : 0, changes.size());
                if (customized) {
                    assertEquals(List.of(BukkitConfigMessages.FAILED), customMessages);
                    assertEquals(List.of("Example > Unable to edit configuration: File changed externally"), messages);
                } else {
                    assertEquals(List.of("Saving configuration...", "Unable to edit configuration: File changed externally"), messages);
                }
            }
        }
    }

    @Test
    public void directEditingResolvesDefaultedValuesAndRejectsSections() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromTomlWithDefaults("[feedback]\nvolume = 0.7\n",
                "[feedback]\nsound = \"minecraft:ui.button.click\"\nvolume = 0.7\n");
        assertEquals("minecraft:ui.button.click",
                BukkitConfigEditor.editableEntry(document, List.of("feedback", "sound")).value().getAsString());
        assertThrows(IllegalArgumentException.class, () -> BukkitConfigEditor.editableEntry(document, List.of("feedback")));
        assertThrows(IllegalArgumentException.class, () -> BukkitConfigEditor.editableEntry(document, List.of("feedback", "missing")));
        assertThrows(IllegalArgumentException.class, () -> BukkitConfigEditor.editableEntry(document, List.of()));
    }

    @Test
    public void closesAnInventoryOpenedWhileTheEditorIsShuttingDown() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class, CommandSender.class));
        Entity entity = (Entity) player;
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        ItemFactory itemFactory = mock(ItemFactory.class);
        AtomicReference<BukkitConfigEditor> editor = new AtomicReference<>();
        when(plugin.getName()).thenReturn("Example");
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.createInventory(any(InventoryHolder.class), eq(54), anyString())).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(((CommandSender) player).hasPermission("example.config")).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(player.openInventory(inventory)).thenAnswer(invocation -> {
            editor.get().close();
            return view;
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(itemFactory);
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(entity), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable opening = invocation.getArgument(2);
                        opening.run();
                        return true;
                    });
            editor.set(BukkitConfigEditor.register(plugin, new BukkitConfigEditor.Options(
                    () -> ConfigEditorDocument.fromToml("enabled = true"),
                    edit -> edit.original(),
                    new BukkitConfigEditor.Presentation("example.config", DirectorMiniMenu.Theme.irisGreen(),
                            DirectorTextResolver.ENGLISH))));

            editor.get().open((CommandSender) player);

            verify(player).closeInventory();
        } finally {
            if (editor.get() != null) {
                editor.get().close();
            }
        }
    }

    @Test
    public void menuSortsSectionsButKeepsRepeatedRulesInTheirOriginalOrder() throws IOException {
        StringBuilder source = new StringBuilder("z = true\na = 1\n");
        for (int index = 0; index < 12; index++) {
            source.append("[[drops]]\nname = \"rule ").append(index).append("\"\n");
        }
        ConfigEditorDocument document = ConfigEditorDocument.fromToml(source.toString());
        ArrayList<String> rootNames = new ArrayList<>();
        for (ConfigEditorDocument.Entry entry : BukkitConfigEditor.orderedEntries(document, List.of())) {
            rootNames.add(entry.name());
        }
        List<ConfigEditorDocument.Entry> drops = BukkitConfigEditor.orderedEntries(document, List.of("drops"));

        assertEquals(List.of("a", "drops", "z"), rootNames);
        assertEquals("rule 2", drops.get(2).value().getAsJsonObject().get("name").getAsString());
        assertEquals("rule 10", drops.get(10).value().getAsJsonObject().get("name").getAsString());
        assertEquals(List.of("drops", "10"), drops.get(10).path());
    }

    @Test
    public void valuePreviewBoundsLoreWithoutChangingTheEditedValue() throws IOException {
        JsonPrimitive value = new JsonPrimitive("line one\nline two\r" + "x".repeat(140));

        String preview = BukkitConfigEditor.preview(value);

        assertEquals(120, preview.length());
        assertTrue(preview.startsWith("line one\\nline two\\r"));
        assertTrue(preview.endsWith("..."));
        assertFalse(preview.contains("\n"));
        assertTrue(value.getAsString().contains("\n"));
        assertEquals("drops / 10 / chance", BukkitConfigEditor.displayPath(List.of("drops", "10", "chance")));
        assertEquals("/", BukkitConfigEditor.displayPath(List.of()));
    }

    @Test
    public void configuredCategoriesKeepCenteredRowsSeparateFromNavigation() {
        assertEquals(List.of(22), BukkitConfigEditor.categorySlots(1));
        assertEquals(List.of(19, 21, 23, 25, 29, 31, 33), BukkitConfigEditor.categorySlots(7));
        assertEquals(List.of(19, 21, 23, 25, 28, 30, 32, 34), BukkitConfigEditor.categorySlots(8));
        for (int count = 0; count <= 8; count++) {
            List<Integer> slots = BukkitConfigEditor.categorySlots(count);
            assertEquals(count, new HashSet<>(slots).size());
            assertTrue(slots.stream().allMatch(slot -> slot >= 18 && slot < 36));
        }
    }

    @Test
    public void configuredSchemaOrdersItsEntriesAndShowsOnlySupportedSettings() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml("alpha = true\nbeta = false\ncustom = 3");
        TextKey label = TextKey.of("test.label", "Setting");
        TextKey description = TextKey.of("test.description", "Setting description");
        BukkitConfigEditor.EditorLayout layout = new BukkitConfigEditor.EditorLayout(label, path -> switch (path.get(0)) {
            case "alpha" -> new BukkitConfigEditor.EntryPresentation(label, description, Material.LEVER, 2, null, null, null);
            case "beta" -> new BukkitConfigEditor.EntryPresentation(label, description, Material.LEVER, 1, null, null, null);
            default -> null;
        }, List.of());

        List<String> configured = BukkitConfigEditor.presentedEntries(document, List.of(), layout).stream()
                .map(ConfigEditorDocument.Entry::name).toList();
        List<String> standard = BukkitConfigEditor.presentedEntries(document, List.of(), null).stream()
                .map(ConfigEditorDocument.Entry::name).toList();

        assertEquals(List.of("beta", "alpha"), configured);
        assertEquals(List.of("alpha", "beta", "custom"), standard);
    }

    @Test
    public void configuredNumberControlsRespectBoundsAndKeepWholeNumberTypes() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml("timing = 600");
        ConfigEditorDocument.Entry entry = document.entries(List.of()).get(0);
        BukkitConfigEditor.NumericControl control = new BukkitConfigEditor.NumericControl(50D, 100D, 3000D);

        JsonPrimitive incremented = BukkitConfigEditor.adjustedValue(entry, control, ClickType.LEFT);
        JsonPrimitive decremented = BukkitConfigEditor.adjustedValue(entry, control, ClickType.RIGHT);
        JsonPrimitive shifted = BukkitConfigEditor.adjustedValue(entry, control, ClickType.SHIFT_LEFT);
        JsonPrimitive bounded = BukkitConfigEditor.adjustedValue(entry, control, ClickType.SHIFT_RIGHT);

        assertEquals(650L, incremented.getAsLong());
        assertEquals(550L, decremented.getAsLong());
        assertEquals(1100L, shifted.getAsLong());
        assertEquals(100L, bounded.getAsLong());
        assertEquals("650", document.edit(entry.path(), incremented).value().toString());
        ConfigEditorDocument.Entry upper = new ConfigEditorDocument.Entry(entry.path(), entry.name(), entry.kind(), new JsonPrimitive(2990L));
        ConfigEditorDocument.Entry lower = new ConfigEditorDocument.Entry(entry.path(), entry.name(), entry.kind(), new JsonPrimitive(100L));
        assertEquals(3000L, BukkitConfigEditor.adjustedValue(upper, control, ClickType.LEFT).getAsLong());
        assertEquals(100L, BukkitConfigEditor.adjustedValue(lower, control, ClickType.RIGHT).getAsLong());
    }

    @Test
    public void configuredDecimalControlsAvoidFloatingPointDrift() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml("chance = 0.2");
        ConfigEditorDocument.Entry entry = document.entries(List.of()).get(0);
        BukkitConfigEditor.NumericControl control = new BukkitConfigEditor.NumericControl(0.1D, 0D, 1D);

        JsonPrimitive incremented = BukkitConfigEditor.adjustedValue(entry, control, ClickType.LEFT);
        JsonPrimitive bounded = BukkitConfigEditor.adjustedValue(entry, control, ClickType.SHIFT_LEFT);

        assertEquals("0.3", incremented.toString());
        assertEquals("0.3", document.edit(entry.path(), incremented).value().toString());
        assertEquals("1.0", document.edit(entry.path(), bounded).value().toString());
    }

    @Test
    public void fractionalControlsAdjustIntegerStorageAndPreserveSourceComments() throws IOException {
        String source = "# Sound settings\nvolume = 1 # Keep this comment\nother = 3\n";
        ConfigEditorDocument document = ConfigEditorDocument.fromToml(source);
        ConfigEditorDocument.Entry entry = document.entries(List.of()).stream()
                .filter(candidate -> candidate.name().equals("volume")).findFirst().orElseThrow();
        BukkitConfigEditor.NumericControl control = new BukkitConfigEditor.NumericControl(0.1D, 0D, 1D);
        JsonPrimitive replacement = BukkitConfigEditor.adjustedValue(entry, control, ClickType.RIGHT);
        ConfigEditorDocument.Edit edit = document.edit(entry.path(), replacement);

        assertEquals("0.9", replacement.toString());
        assertEquals("1.0", BukkitConfigEditor.adjustedValue(entry, control, ClickType.LEFT).toString());
        assertEquals("0.0", BukkitConfigEditor.adjustedValue(entry, control, ClickType.SHIFT_RIGHT).toString());
        assertEquals(source, edit.original().source());
        assertEquals("# Sound settings\nvolume = 0.9 # Keep this comment\nother = 3\n",
                TomlDocumentEditor.set(edit.original().source(), edit.path(), edit.value()));
        ConfigEditorDocument.Entry whole = document.entries(List.of()).stream()
                .filter(candidate -> candidate.name().equals("other")).findFirst().orElseThrow();
        assertEquals("4.5", BukkitConfigEditor.adjustedValue(whole,
                new BukkitConfigEditor.NumericControl(1.5D, 0D, 10D), ClickType.LEFT).toString());
    }

    @Test
    public void fractionalControlsAcceptDecimalChatInputAndUseDecimalGuidance() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml("volume = 1");
        ConfigEditorDocument.Entry entry = document.entries(List.of()).get(0);
        TextKey label = TextKey.of("test.volume", "Volume");
        BukkitConfigEditor.EntryPresentation fractional = new BukkitConfigEditor.EntryPresentation(
                label, label, Material.NOTE_BLOCK, 0, new BukkitConfigEditor.NumericControl(0.1D, 0D, 1D), null, null);
        BukkitConfigEditor.EntryPresentation integer = new BukkitConfigEditor.EntryPresentation(
                label, label, Material.CLOCK, 0, new BukkitConfigEditor.NumericControl(1D, 0D, 100D), null, null);

        assertEquals("0.35", document.edit(entry.path(),
                BukkitConfigEditor.parseInput(document, entry, fractional, " 0.35 ")).value().toString());
        assertEquals(BukkitConfigMessages.DECIMAL, BukkitConfigEditor.inputGuidance(entry.kind(), fractional));
        assertEquals(BukkitConfigMessages.INTEGER, BukkitConfigEditor.inputGuidance(entry.kind(), integer));
        assertThrows(IllegalArgumentException.class, () -> BukkitConfigEditor.parseInput(document, entry, fractional, "NaN"));
        assertThrows(IllegalArgumentException.class, () -> BukkitConfigEditor.parseInput(document, entry, fractional, "1e999"));
        assertThrows(IllegalArgumentException.class, () -> BukkitConfigEditor.parseInput(document, entry, fractional, "text"));
        assertThrows(IllegalArgumentException.class, () -> BukkitConfigEditor.parseInput(document, entry, integer, "0.35"));
        assertThrows(IllegalArgumentException.class, () -> BukkitConfigEditor.parseInput(document, entry, null, "0.35"));
        BukkitConfigEditor.EntryPresentation largerFraction = new BukkitConfigEditor.EntryPresentation(
                label, label, Material.CLOCK, 0, new BukkitConfigEditor.NumericControl(1.5D, 0D, 10D), null, null);
        assertEquals("4.5", BukkitConfigEditor.parseInput(document, entry, largerFraction, "4.5").toString());
    }

    @Test
    public void sharedFeedbackCatalogHasValidCompletePlaceholderDefinitions() {
        MessageCatalog catalog = MessageCatalog.builder("en_US").addAll(BukkitConfigMessages.keys())
                .addAll(BukkitConfigMessages.configuredLayoutKeys()).build();
        assertEquals(BukkitConfigMessages.keys().size() + BukkitConfigMessages.configuredLayoutKeys().size(), catalog.ids().size());
        for (MessageKey definition : catalog.keys()) {
            TextKey key = (TextKey) definition;
            MessageArgs.Builder arguments = MessageArgs.builder();
            for (String placeholder : key.placeholders()) {
                arguments.untrusted(placeholder, "example");
            }
            String rendered = DirectorTextResolver.ENGLISH.resolve(key, arguments.build());
            assertFalse(key.id(), rendered.isBlank());
            assertFalse(key.id(), rendered.contains("{"));
        }
    }
}
