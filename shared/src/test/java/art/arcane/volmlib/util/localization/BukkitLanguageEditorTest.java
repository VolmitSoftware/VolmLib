package art.arcane.volmlib.util.localization;

import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    public void variablesIncludeThePluralSelectorAndAreSorted() {
        PluralKey key = PluralKey.of("test.plural", "count", Map.of("other", "Hello {name}"));

        assertEquals("{count} {name}", BukkitLanguageEditor.variables(key));
        assertEquals("None", BukkitLanguageEditor.variables(TextKey.of("test.text", "Hello")));
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
}
