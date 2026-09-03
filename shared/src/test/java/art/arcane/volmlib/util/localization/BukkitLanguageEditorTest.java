package art.arcane.volmlib.util.localization;

import org.bukkit.ChatColor;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BukkitLanguageEditorTest {
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
}
