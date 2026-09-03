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
    public void inventoryViewAccessUsesRuntimeMethod() {
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);

        assertSame(inventory, BukkitLanguageEditor.inventoryViewTopInventory(view));
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
