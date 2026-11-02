package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.inventorygui.BukkitInventoryShutdown;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class BukkitLanguageEditorTest {
    private static final String INVENTORY_VIEW_INTERNAL_NAME = "org/bukkit/inventory/InventoryView";

    @Test(timeout = 10_000L)
    public void managedCloseWaitsForAnInventoryStillBeingOpened() throws Exception {
        verifyCloseDuringOpening(false);
    }

    @Test(timeout = 10_000L)
    public void failedCloseDuringOpeningKeepsTheInventoryGuardedAndRetryable() throws Exception {
        verifyCloseDuringOpening(true);
    }

    private void verifyCloseDuringOpening(boolean failClose) throws Exception {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class, CommandSender.class));
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        ItemFactory itemFactory = mock(ItemFactory.class);
        PluginLanguageService languages = mock(PluginLanguageService.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getName()).thenReturn("Example");
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.getServer()).thenReturn(server);
        when(server.createInventory(any(InventoryHolder.class), eq(54), anyString())).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(((CommandSender) player).hasPermission("volmit.language.admin")).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(languages.availableLocales()).thenReturn(List.of());
        BukkitLanguageEditor editor = editor(plugin, languages);
        AtomicReference<CompletableFuture<Void>> completion = new AtomicReference<>();
        when(player.openInventory(inventory)).thenAnswer(invocation -> {
            completion.set(editor.closeAsync());
            assertFalse("Cleanup must include the inventory still being opened", completion.get().isDone());
            return view;
        });
        IllegalStateException closeFailure = new IllegalStateException("Inventory close failed");
        if (failClose) {
            doThrow(closeFailure).doNothing().when(player).closeInventory();
        }
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(itemFactory);
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) player)).thenReturn(true);
            if (failClose) {
                assertSame(closeFailure, assertThrows(IllegalStateException.class, () -> editor.open(player, null)));
                assertSame(closeFailure, assertThrows(CompletionException.class, () -> completion.get().join()).getCause());
                assertEquals(1, openInventories(editor).size());
                handlers.verify(() -> HandlerList.unregisterAll(editor), never());
                editor.closeAsync().join();
                assertTrue(completion.get().isCompletedExceptionally());
                assertTrue(openInventories(editor).isEmpty());
            } else {
                editor.open(player, null);
                verify(player).closeInventory();
                completion.get().join();
            }
        } finally {
            editor.close();
        }
    }

    @Test(timeout = 2000L)
    public void enabledCloseKeepsAnAcceptedOwnerCleanupPending() throws Exception {
        CleanupFixture fixture = new CleanupFixture();
        AtomicReference<Runnable> cleanup = new AtomicReference<>();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class)) {
            fixture.captureCleanup(scheduler, cleanup);
            fixture.editor.close();
            CompletableFuture<Void> completion = fixture.editor.closeAsync();
            fixture.editor.close();
            assertFalse(completion.isDone());
            handlers.verify(() -> HandlerList.unregisterAll(fixture.editor), never());
            verify(fixture.player, never()).getOpenInventory();
            cleanup.get().run();
            completion.join();
            verify(fixture.player).closeInventory();
            handlers.verify(() -> HandlerList.unregisterAll(fixture.editor));
            assertTrue(fixture.inventories.isEmpty());
        }
    }

    @Test(timeout = 2000L)
    public void failedCleanupRetainsGuardsAndCanBeRetried() throws Exception {
        CleanupFixture fixture = new CleanupFixture();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class)) {
            CompletableFuture<Void> failed = fixture.editor.closeAsync();
            assertThrows(CompletionException.class, failed::join);
            assertEquals(1, fixture.inventories.size());
            handlers.verify(() -> HandlerList.unregisterAll(fixture.editor), never());
            fixture.verifyGuards();
            AtomicReference<Runnable> cleanup = new AtomicReference<>();
            fixture.captureCleanup(scheduler, cleanup);
            CompletableFuture<Void> retry = fixture.editor.closeAsync();
            assertFalse(retry.isDone());
            assertTrue(failed.isCompletedExceptionally());
            cleanup.get().run();
            retry.join();
            assertTrue(failed.isCompletedExceptionally());
            assertTrue(fixture.inventories.isEmpty());
            handlers.verify(() -> HandlerList.unregisterAll(fixture.editor));
        }
    }

    @Test(timeout = 2000L)
    public void failedCleanupReleasesGuardsOnlyAfterTheTrackedViewCloses() throws Exception {
        CleanupFixture fixture = new CleanupFixture();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class)) {
            assertThrows(CompletionException.class, () -> fixture.editor.closeAsync().join());
            InventoryCloseEvent close = mock(InventoryCloseEvent.class);
            when(close.getPlayer()).thenReturn((HumanEntity) fixture.player);
            when(close.getInventory()).thenReturn(mock(Inventory.class));
            fixture.editor.onClose(close);
            handlers.verify(() -> HandlerList.unregisterAll(fixture.editor), never());
            assertEquals(1, fixture.inventories.size());
            when(close.getInventory()).thenReturn(fixture.inventory);
            fixture.editor.onClose(close);
            assertTrue(fixture.inventories.isEmpty());
            handlers.verify(() -> HandlerList.unregisterAll(fixture.editor));
        }
    }

    @Test(timeout = 2000L)
    public void failedCleanupReleasesGuardsWhenTheLastViewerQuits() throws Exception {
        CleanupFixture fixture = new CleanupFixture();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class)) {
            assertThrows(CompletionException.class, () -> fixture.editor.closeAsync().join());
            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(fixture.player);
            fixture.editor.onQuit(quit);
            assertTrue(fixture.inventories.isEmpty());
            handlers.verify(() -> HandlerList.unregisterAll(fixture.editor));
        }
    }

    @Test
    public void commandRetiresQueuedChatWithoutClosingOrReplacingAnotherView() throws Exception {
        Plugin plugin = mock(Plugin.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class));
        Entity entity = (Entity) player;
        UUID owner = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(owner);
        BukkitLanguageEditor editor = editor(plugin, mock(PluginLanguageService.class));
        Constructor<?> viewConstructor = nested("View").getDeclaredConstructors()[0];
        viewConstructor.setAccessible(true);
        Object view = viewConstructor.newInstance(null, null, null, "", 1, null, (Consumer<Player>) ignored -> { });
        Constructor<?> promptConstructor = nested("Prompt").getDeclaredConstructors()[0];
        promptConstructor.setAccessible(true);
        Object prompt = promptConstructor.newInstance(view, null, null, null);
        views(editor).put(owner, view);
        prompts(editor).put(owner, prompt);
        AsyncPlayerChatEvent chat = mock(AsyncPlayerChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        when(chat.getMessage()).thenReturn("old filter");
        PlayerCommandPreprocessEvent command = mock(PlayerCommandPreprocessEvent.class);
        when(command.getPlayer()).thenReturn(player);
        when(command.isCancelled()).thenReturn(true);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(same(plugin), same(entity), any(Runnable.class),
                    eq(0L), any(Runnable.class))).thenAnswer(invocation -> {
                        queued.set(invocation.getArgument(2));
                        return true;
                    });
            editor.onChat(chat);
            assertSame(prompt, prompts(editor).get(owner));
            editor.onCommand(command);
            assertTrue(prompts(editor).isEmpty());
            assertTrue(views(editor).isEmpty());
            Object replacement = viewConstructor.newInstance(null, null, null, "new filter", 1, null, (Consumer<Player>) ignored -> { });
            views(editor).put(owner, replacement);
            queued.get().run();
            editor.onCommand(command);
            assertSame(replacement, views(editor).get(owner));
        }
        verify(chat).setCancelled(true);
        verify(player, never()).closeInventory();
    }

    @Test
    public void inputDecodesNewlinesAndLiteralBackslashesWithoutChangingOtherEscapes() {
        assertEquals("first\nsecond", BukkitLanguageEditor.decodeInput("first\\nsecond"));
        assertEquals("path\\name\\tvalue", BukkitLanguageEditor.decodeInput("path\\\\name\\tvalue"));
        assertEquals("trailing\\", BukkitLanguageEditor.decodeInput("trailing\\"));
    }

    @Test
    public void multilineEditingPreservesOtherLinesAndMessageShape() {
        MessageValue changed = BukkitLanguageEditor.replacement(new LinesValue(List.of("first", "old", "last")), "1", "");

        assertEquals(new LinesValue(List.of("first", "", "last")), changed);
        assertEquals("first\n\nlast", BukkitLanguageEditor.rawValue(changed, null));
        assertEquals("", BukkitLanguageEditor.rawValue(changed, "1"));
    }

    @Test
    public void editingOnePluralFormPreservesOtherForms() {
        PluralValue original = new PluralValue(Map.of("one", "One {name}", "other", "{count} {name}"));

        MessageValue changed = BukkitLanguageEditor.replacement(original, "one", "Single {name}");

        assertEquals(new PluralValue(Map.of("one", "Single {name}", "other", "{count} {name}")), changed);
        assertEquals("{count} {name}", BukkitLanguageEditor.rawValue(original, "few"));
        assertEquals("Single {name}", BukkitLanguageEditor.rawValue(changed, "one"));
        assertEquals(2, original.forms().size());
    }

    @Test
    public void textEditingPreservesEmptyTextAndLiteralNewlines() {
        assertEquals(new TextValue(""), BukkitLanguageEditor.replacement(new TextValue("old"), null, ""));
        assertEquals("first\nsecond", BukkitLanguageEditor.rawValue(new TextValue("first\nsecond"), null));
    }

    @Test
    public void variableNamesIncludeThePluralSelectorAndAreSorted() {
        PluralKey key = PluralKey.of("test.plural", "count", Map.of("other", "Hello {name}"));

        assertEquals("{count} {name}", BukkitLanguageEditor.variableNames(key));
        assertEquals("", BukkitLanguageEditor.variableNames(TextKey.of("test.text", "Hello")));
    }

    @Test
    public void editorChromeUsesThePlayersSelectedLocalizationSnapshot() {
        TextKey key = BukkitLanguageMessages.EDITOR_BACK;
        MessageCatalog catalog = MessageCatalog.of("en_US", key);
        LocaleOverlay french = LocaleOverlay.builder("fr_FR")
                .text(key.id(), "<gold>Retour</gold>")
                .build();
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
                catalog,
                List.of(french),
                PluralSelector.oneOther()
        ));
        PluginLanguageService languages = mock(PluginLanguageService.class);
        Plugin plugin = mock(Plugin.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(languages.snapshot(playerId)).thenReturn(snapshot);
        PluginLanguageEditor.Options editorOptions = new PluginLanguageEditor.Options(
                locale -> snapshot,
                edit -> snapshot
        );
        BukkitLanguageSwitcher.Options switcher = new BukkitLanguageSwitcher.Options(
                "test",
                "test.language.admin",
                DirectorMiniMenu.Theme.adaptRed(),
                DirectorTextResolver.ENGLISH,
                editorOptions,
                null,
                null,
                null
        );
        BukkitLanguageEditor editor = new BukkitLanguageEditor(
                plugin,
                new BukkitLanguageEditor.Options(languages, switcher, ignored -> {
                }, BukkitLanguageEditorPresentation.standard())
        );

        assertTrue(editor.localized(player, key).legacy().contains("§6Retour"));
    }

    @Test
    public void nativeInventoryTitleUsesUnformattedLocalizedTextAcrossThemeChanges() {
        TextKey key = BukkitLanguageMessages.EDITOR_LANGUAGES;
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
                MessageCatalog.of("en_US", key),
                List.of(LocaleOverlay.builder("fr_FR").text(key.id(), "<gold>Langues</gold>").build()),
                PluralSelector.oneOther()
        ));
        PluginLanguageService languages = mock(PluginLanguageService.class);
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class, CommandSender.class));
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(((CommandSender) player).hasPermission("volmit.language.admin")).thenReturn(true);
        when(languages.snapshot(playerId)).thenReturn(snapshot);
        when(languages.availableLocales()).thenReturn(List.of("fr_FR"));
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getName()).thenReturn("Test<green>&c[ff0000]§c");
        when(plugin.getServer()).thenReturn(server);
        IllegalStateException inventoryBoundary = new IllegalStateException("Inventory factory boundary");
        List<String> titles = new ArrayList<>();
        when(server.createInventory(any(InventoryHolder.class), eq(54), anyString())).thenAnswer(invocation -> {
            titles.add(invocation.getArgument(2, String.class));
            throw inventoryBoundary;
        });
        BukkitLanguageEditor editor = editor(plugin, languages);

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) player)).thenReturn(true);
            assertSame(inventoryBoundary, assertThrows(IllegalStateException.class, () -> editor.open(player, null)));
            editor.updateTheme(DirectorMiniMenu.Theme.irisGreen());
            assertSame(inventoryBoundary, assertThrows(IllegalStateException.class, () -> editor.open(player, null)));
        }

        assertEquals(List.of("Test<green>&c[ff0000] › Langues", "Test<green>&c[ff0000] › Langues"), titles);
    }

    @Test
    public void formattedPreviewsRetainColorsAcrossBoundedLines() {
        List<String> lines = BukkitLanguageEditor.preview("<light_purple>1234567890abcdef</light_purple>\n<green>next</green>", 10, 6);

        assertEquals(3, lines.size());
        assertTrue(lines.get(1).startsWith("§d"));
        assertEquals("next", ChatColor.stripColor(lines.get(2)));
        for (String line : lines) {
            assertTrue(ChatColor.stripColor(line).length() <= 10);
        }
    }

    @Test
    public void excessivePreviewHeightIsTruncated() {
        List<String> lines = BukkitLanguageEditor.preview("x".repeat(200), 10, 3);

        assertEquals(3, lines.size());
        assertTrue(lines.get(2).endsWith("§8..."));
        assertEquals(List.of("(empty)"), BukkitLanguageEditor.preview("", 10, 3));
    }

    @Test
    public void languageEditorGroupsEveryCatalogByItsTopLevelKeySegment() {
        MessageCatalog catalog = MessageCatalog.of("en_US",
                TextKey.of("gui.prompt.cancel", "Cancel"),
                TextKey.of("portal.created", "Created"),
                TextKey.of("command.description.root", "Root"),
                TextKey.of("runtime.prefix", "Prefix"),
                TextKey.of("portal.failed", "Failed"),
                TextKey.of("custom", "Custom"));
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(
                LocalizationCandidate.english(catalog, PluralSelector.oneOther()));
        PluginLanguageEditor.Document document = new PluginLanguageEditor.Document("en_US", snapshot);

        assertEquals(List.of("command", "custom", "gui", "portal", "runtime"),
                BukkitLanguageEditor.groups(document));
        assertEquals("director", BukkitLanguageEditor.group("director.help.page"));
        assertEquals("GUI", BukkitLanguageEditor.groupName("gui"));
        assertEquals("API", BukkitLanguageEditor.groupName("api"));
        assertEquals("Hot reload", BukkitLanguageEditor.groupName("hot_reload"));
    }

    @Test
    public void languageEditorSearchCanMatchMessagesAcrossEveryCategory() {
        MessageCatalog catalog = MessageCatalog.of("en_US",
                TextKey.of("command.create", "Create a portal"),
                TextKey.of("portal.created", "Portal created"),
                TextKey.of("runtime.ready", "Ready"));
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(
                LocalizationCandidate.english(catalog, PluralSelector.oneOther()));
        PluginLanguageEditor.Document document = new PluginLanguageEditor.Document("en_US", snapshot);

        assertEquals(List.of("command.create", "portal.created"),
                BukkitLanguageEditor.matchingKeys(document, null, "portal").stream().map(MessageKey::id).toList());
        assertEquals(List.of("portal.created"),
                BukkitLanguageEditor.matchingKeys(document, "portal", "created").stream().map(MessageKey::id).toList());
    }

    @Test
    public void inventoryViewAccessUsesRuntimeMethod() {
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);

        assertSame(inventory, BukkitLanguageEditor.inventoryViewTopInventory(view));
    }

    @Test
    public void inventoryLanguageTilesUseTheShapedPortalStyleWithoutReload() {
        String active = BukkitLanguageEditor.localeTitle("fr_FR", "French (France)", true).legacy();
        String inactive = BukkitLanguageEditor.localeTitle("de_DE", "German (Germany)", false).legacy();

        assertEquals("§a✔§r §ffr_FR§r §8—§r §7French (France)", active);
        assertEquals("§8•§r §fde_DE§r §8—§r §7German (Germany)", inactive);
        assertTrue(BukkitLanguageEditor.categoryTitle("Command").legacy().contains("§dCommand"));
        assertEquals(List.of(20, 21, 22, 23, 24), BukkitLanguageEditor.categorySlots(5));
        assertEquals(List.of(11, 12, 13, 14, 20, 21, 22, 23), BukkitLanguageEditor.categorySlots(8));
        assertEquals(Set.of(45, 48, 49, 50, 53), BukkitLanguageEditor.navigationSlots());
    }

    @Test
    public void overriddenEditorTitleUsesThePlayersResolverAndCustomName() {
        TextKey key = BukkitLanguageMessages.EDITOR_TITLE;
        TextKey overridden = TextKey.of(key.id(), "{prefix} › {section}");
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
                MessageCatalog.of("en_US", overridden), List.of(), PluralSelector.oneOther()));
        PluginLanguageService languages = mock(PluginLanguageService.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(languages.snapshot(playerId)).thenReturn(snapshot);
        DirectorTextResolver resolver = (requested, arguments) -> {
            assertEquals(key, requested);
            assertEquals(playerId, LanguageAudience.current());
            return "<gold><bold>Custom</bold></gold><gray> › "
                    + ComponentText.literal(String.valueOf(arguments.require("section").value())).miniMessage()
                    + "</gray>";
        };
        BukkitLanguageSwitcher.Options switcher = new BukkitLanguageSwitcher.Options(
                "test", "test.language.admin", DirectorMiniMenu.Theme.adaptRed(), resolver,
                new PluginLanguageEditor.Options(locale -> snapshot, edit -> snapshot), null, null, null);
        BukkitLanguageEditor editor = new BukkitLanguageEditor(mock(Plugin.class),
                new BukkitLanguageEditor.Options(languages, switcher, ignored -> {
                }, BukkitLanguageEditorPresentation.standard()));

        ComponentText rendered = editor.localized(player, key,
                MessageArgument.untrusted("plugin", "Original"),
                MessageArgument.untrusted("section", "<red>Messages"));

        assertEquals("Custom › <red>Messages", rendered.plain());
        assertTrue(rendered.legacy().contains("§6§lCustom"));
        assertEquals(null, LanguageAudience.current());
    }

    @Test
    public void fourRowLayoutUsesEverySlotAndRejectsUnusedFifthRowClicks() throws Exception {
        List<Integer> slots = BukkitLanguageEditor.categorySlots(40, BukkitLanguageEditorPresentation.Layout.FOUR_ROWS);
        assertEquals(36, slots.size());
        for (int slot = 0; slot < 36; slot++) {
            assertEquals(Integer.valueOf(slot), slots.get(slot));
        }
        assertEquals(List.of(0, 1, 2),
                BukkitLanguageEditor.categorySlots(3, BukkitLanguageEditorPresentation.Layout.FOUR_ROWS));
        Plugin plugin = mock(Plugin.class);
        PluginLanguageService languages = mock(PluginLanguageService.class);
        BukkitLanguageSwitcher.Options switcher = new BukkitLanguageSwitcher.Options(
                "test", "test.language.admin", DirectorMiniMenu.Theme.adaptRed(),
                DirectorTextResolver.ENGLISH, new PluginLanguageEditor.Options(locale -> null, edit -> null), null, null, null);
        BukkitLanguageEditor editor = new BukkitLanguageEditor(plugin, new BukkitLanguageEditor.Options(
                languages, switcher, ignored -> {
                }, new BukkitLanguageEditorPresentation(BukkitLanguageEditorPresentation.Layout.FOUR_ROWS,
                ignored -> Optional.empty())));
        MessageCatalog.Builder catalog = MessageCatalog.builder("en_US");
        for (int index = 0; index < 40; index++) {
            catalog.add(TextKey.of("group" + index + ".message", "Message"));
        }
        PluginLanguageEditor.Document document = new PluginLanguageEditor.Document("en_US",
                LocalizationSnapshot.create(LocalizationCandidate.english(catalog.build(), PluralSelector.oneOther())));
        Constructor<?> constructor = nested("View").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object categories = constructor.newInstance("en_US", document, null, "", 2, null,
                (Consumer<Player>) ignored -> {
                });
        Object messages = constructor.newInstance("en_US", document, null, "message", 2, null,
                (Consumer<Player>) ignored -> {
                });
        Method ordinal = BukkitLanguageEditor.class.getDeclaredMethod("contentOrdinal", nested("View"), int.class);
        ordinal.setAccessible(true);
        Method pageSize = BukkitLanguageEditor.class.getDeclaredMethod("pageSize", nested("View"));
        pageSize.setAccessible(true);

        assertEquals(36, pageSize.invoke(editor, categories));
        assertEquals(36, pageSize.invoke(editor, messages));
        assertEquals(3, ordinal.invoke(editor, categories, 3));
        assertEquals(-1, ordinal.invoke(editor, categories, 4));
        assertEquals(35, ordinal.invoke(editor, messages, 35));
        assertEquals(-1, ordinal.invoke(editor, messages, 36));
        assertEquals(-1, ordinal.invoke(editor, messages, 44));
    }

    @Test
    public void customIconsAreClonedBeforeEditorMetadataIsApplied() {
        ItemStack template = mock(ItemStack.class);
        ItemStack copy = mock(ItemStack.class);
        when(template.getType()).thenReturn(Material.DIAMOND_AXE);
        when(template.clone()).thenReturn(copy);
        BukkitLanguageEditorPresentation presentation = new BukkitLanguageEditorPresentation(
                BukkitLanguageEditorPresentation.Layout.FOUR_ROWS, ignored -> Optional.of(template));

        ItemStack icon = presentation.icon("axe.wood_miner.name", Material.PAPER);

        assertSame(copy, icon);
        assertNotSame(template, icon);
        verify(template).clone();
        assertEquals(Material.BOOK, BukkitLanguageEditorPresentation.standard().icon("command", Material.BOOK).getType());
    }

    @Test
    public void languageEditorBytecodeDoesNotBindInventoryViewInvocationKind() throws IOException {
        List<String> directCalls = new ArrayList<>();
        try (InputStream bytecode = BukkitLanguageEditor.class.getResourceAsStream("BukkitLanguageEditor.class")) {
            assertNotNull(bytecode);
            ClassReader reader = new ClassReader(bytecode);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor,
                                                    boolean isInterface) {
                            if (INVENTORY_VIEW_INTERNAL_NAME.equals(owner)) {
                                directCalls.add(methodName + methodDescriptor);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue("Direct InventoryView calls bind bytecode to either the class or interface ABI: " + directCalls,
                directCalls.isEmpty());
    }

    @Test
    public void rejectedChatContinuationRetiresThePromptAndView() throws Exception {
        PluginLanguageService languages = mock(PluginLanguageService.class);
        Plugin plugin = mock(Plugin.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class));
        Entity entity = (Entity) player;
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        BukkitLanguageSwitcher.Options switcher = new BukkitLanguageSwitcher.Options(
                "test",
                "test.language.admin",
                DirectorMiniMenu.Theme.adaptRed(),
                DirectorTextResolver.ENGLISH,
                new PluginLanguageEditor.Options(locale -> null, edit -> null),
                null,
                null,
                null
        );
        BukkitLanguageEditor editor = new BukkitLanguageEditor(
                plugin,
                new BukkitLanguageEditor.Options(languages, switcher, ignored -> {
                }, BukkitLanguageEditorPresentation.standard())
        );
        Constructor<?> viewConstructor = nested("View").getDeclaredConstructors()[0];
        viewConstructor.setAccessible(true);
        Object view = viewConstructor.newInstance(
                null, null, null, "", 1, null, (Consumer<Player>) ignored -> {
                }
        );
        Constructor<?> promptConstructor = nested("Prompt").getDeclaredConstructors()[0];
        promptConstructor.setAccessible(true);
        Object prompt = promptConstructor.newInstance(view, null, null, null);
        views(editor).put(playerId, view);
        prompts(editor).put(playerId, prompt);
        AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getMessage()).thenReturn("value");

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(
                    same(plugin), same(entity), any(Runnable.class), eq(0L), any(Runnable.class)
            )).thenReturn(false);
            editor.onChat(event);
        }

        verify(event).setCancelled(true);
        assertTrue(prompts(editor).isEmpty());
        assertTrue(views(editor).isEmpty());
    }

    @Test
    public void pluginDisableClosesEditorInventoryOnCurrentOwner() throws Exception {
        PluginLanguageService languages = mock(PluginLanguageService.class);
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class));
        Entity entity = (Entity) player;
        Inventory inventory = mock(Inventory.class);
        InventoryView inventoryView = mock(InventoryView.class);
        PluginDisableEvent disableEvent = mock(PluginDisableEvent.class);
        BukkitLanguageEditor editor = editor(plugin, languages);
        InventoryHolder holder = holder(editor);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(player.getOpenInventory()).thenReturn(inventoryView);
        when(inventoryView.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(disableEvent.getPlugin()).thenReturn(plugin);
        openInventories(editor).put(UUID.randomUUID(), new BukkitInventoryShutdown.View(player, inventory));

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(true);

            editor.onPluginDisable(disableEvent);
        }

        verify(player).closeInventory();
    }

    @Test
    public void rejectedPreDisableCloseRetiresWithoutOffOwnerInventoryAccess() throws Exception {
        PluginLanguageService languages = mock(PluginLanguageService.class);
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class));
        Entity entity = (Entity) player;
        PluginDisableEvent disableEvent = mock(PluginDisableEvent.class);
        BukkitLanguageEditor editor = editor(plugin, languages);
        openInventories(editor).put(UUID.randomUUID(), new BukkitInventoryShutdown.View(player, mock(Inventory.class)));
        when(plugin.getServer()).thenReturn(server);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(disableEvent.getPlugin()).thenReturn(plugin);

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(false);
            scheduler.when(() -> FoliaScheduler.runEntity(
                    same(plugin), same(entity), any(Runnable.class), eq(0L), any(Runnable.class)
            )).thenReturn(false);

            editor.onPluginDisable(disableEvent);
        }

        verify(player, never()).getOpenInventory();
        verify(player, never()).closeInventory();
    }

    @Test(timeout = 2000L)
    public void disableTerminatesPendingManagedCloseWithoutAccessingAnotherOwner() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class));
        Entity entity = (Entity) player;
        BukkitLanguageEditor editor = editor(plugin, mock(PluginLanguageService.class));
        openInventories(editor).put(UUID.randomUUID(), new BukkitInventoryShutdown.View(player, mock(Inventory.class)));
        AtomicReference<Runnable> cleanup = new AtomicReference<>();
        PluginDisableEvent disable = mock(PluginDisableEvent.class);
        when(disable.getPlugin()).thenReturn(plugin);
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(same(plugin), same(entity), any(Runnable.class),
                    eq(0L), any(Runnable.class))).thenAnswer(invocation -> {
                cleanup.set(invocation.getArgument(2, Runnable.class));
                return true;
            });
            CompletableFuture<Void> completion = editor.closeAsync();
            assertFalse(completion.isDone());
            editor.onPluginDisable(disable);
            assertTrue(completion.isCompletedExceptionally());
            cleanup.get().run();
            verify(player, never()).getOpenInventory();
            verify(player, never()).closeInventory();
        }
    }

    private BukkitLanguageEditor editor(Plugin plugin, PluginLanguageService languages) {
        BukkitLanguageSwitcher.Options switcher = new BukkitLanguageSwitcher.Options(
                "test",
                "test.language.admin",
                DirectorMiniMenu.Theme.adaptRed(),
                DirectorTextResolver.ENGLISH,
                new PluginLanguageEditor.Options(locale -> null, edit -> null),
                null,
                null,
                null
        );
        return new BukkitLanguageEditor(
                plugin,
                new BukkitLanguageEditor.Options(languages, switcher, ignored -> {
                }, BukkitLanguageEditorPresentation.standard())
        );
    }

    private final class CleanupFixture {
        private final Plugin plugin = mock(Plugin.class);
        private final Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class, HumanEntity.class));
        private final Entity entity = (Entity) player;
        private final Inventory inventory = mock(Inventory.class);
        private final InventoryView view = mock(InventoryView.class);
        private final BukkitLanguageEditor editor;
        private final Map<UUID, BukkitInventoryShutdown.View> inventories;

        private CleanupFixture() throws Exception {
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getOpenInventory()).thenReturn(view);
            when(view.getTopInventory()).thenReturn(inventory);
            editor = editor(plugin, mock(PluginLanguageService.class));
            inventories = openInventories(editor);
            inventories.put(player.getUniqueId(), new BukkitInventoryShutdown.View(player, inventory));
        }

        private void captureCleanup(MockedStatic<FoliaScheduler> scheduler, AtomicReference<Runnable> cleanup) {
            scheduler.when(() -> FoliaScheduler.runEntity(same(plugin), same(entity), any(Runnable.class),
                    eq(0L), any(Runnable.class))).thenAnswer(invocation -> {
                cleanup.set(invocation.getArgument(2));
                return true;
            });
        }

        private void verifyGuards() throws Exception {
            when(inventory.getHolder()).thenReturn(holder(editor));
            InventoryClickEvent click = mock(InventoryClickEvent.class);
            when(click.getView()).thenReturn(view);
            editor.onClick(click);
            verify(click).setCancelled(true);
            InventoryDragEvent drag = mock(InventoryDragEvent.class);
            when(drag.getView()).thenReturn(view);
            editor.onDrag(drag);
            verify(drag).setCancelled(true);
        }
    }

    private InventoryHolder holder(BukkitLanguageEditor editor) throws Exception {
        Constructor<?> constructor = nested("Holder").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (InventoryHolder) constructor.newInstance(editor, null);
    }

    private Class<?> nested(String name) {
        for (Class<?> nested : BukkitLanguageEditor.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals(name)) {
                return nested;
            }
        }
        throw new AssertionError("Missing nested type " + name);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, BukkitInventoryShutdown.View> openInventories(BukkitLanguageEditor editor) throws Exception {
        Field field = BukkitLanguageEditor.class.getDeclaredField("openInventories");
        field.setAccessible(true);
        return (Map<UUID, BukkitInventoryShutdown.View>) field.get(editor);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Object> views(BukkitLanguageEditor editor) throws Exception {
        Field field = BukkitLanguageEditor.class.getDeclaredField("views");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(editor);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Object> prompts(BukkitLanguageEditor editor) throws Exception {
        Field field = BukkitLanguageEditor.class.getDeclaredField("prompts");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(editor);
    }
}
