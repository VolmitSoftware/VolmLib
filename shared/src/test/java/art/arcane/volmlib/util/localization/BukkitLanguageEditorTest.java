package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.inventorygui.BukkitInventoryShutdown;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
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
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class BukkitLanguageEditorTest {
    private static final String INVENTORY_VIEW_INTERNAL_NAME = "org/bukkit/inventory/InventoryView";

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
                editorOptions
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
        when(plugin.getName()).thenReturn("Test<green>");
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

        assertEquals(List.of("Test<green> › Langues", "Test<green> › Langues"), titles);
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
                DirectorTextResolver.ENGLISH, new PluginLanguageEditor.Options(locale -> null, edit -> null));
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
                new PluginLanguageEditor.Options(locale -> null, edit -> null)
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
    public void pluginDisableDrainsEditorInventoryWithTheStillEnabledOwner() throws Exception {
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
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(false);
            scheduler.when(() -> FoliaScheduler.runEntity(
                    same(plugin), same(entity), any(Runnable.class), eq(0L), any(Runnable.class)
            )).thenAnswer(invocation -> {
                assertTrue(plugin.isEnabled());
                invocation.getArgument(2, Runnable.class).run();
                return true;
            });

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

    private BukkitLanguageEditor editor(Plugin plugin, PluginLanguageService languages) {
        BukkitLanguageSwitcher.Options switcher = new BukkitLanguageSwitcher.Options(
                "test",
                "test.language.admin",
                DirectorMiniMenu.Theme.adaptRed(),
                DirectorTextResolver.ENGLISH,
                new PluginLanguageEditor.Options(locale -> null, edit -> null)
        );
        return new BukkitLanguageEditor(
                plugin,
                new BukkitLanguageEditor.Options(languages, switcher, ignored -> {
                }, BukkitLanguageEditorPresentation.standard())
        );
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
