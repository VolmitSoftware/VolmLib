package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TomlLanguageParserTest {
    @Test
    public void parsesSectionedTextValues() throws Exception {
        String source = "# Reference\n[runtime]\nprefix = \"&dPortal &8> \"\n"
                + "[command.feedback]\nsaved = \"{prefix}&aSaved {setting}\"\n";

        assertEquals(Map.of(
                "runtime.prefix", "&dPortal &8> ",
                "command.feedback.saved", "{prefix}&aSaved {setting}"
        ), TomlLanguageParser.parseText(source));
    }

    @Test
    public void preservesEscapedBackslashBeforeEscapeLetters() throws Exception {
        String source = "[editor]\nguidance = \"Type \\\\n for a new line and \\\\t for a tab token.\"\n";

        assertEquals(
                "Type \\n for a new line and \\t for a tab token.",
                TomlLanguageParser.parseText(source).get("editor.guidance")
        );
    }

    @Test
    public void rejectsNonTextValues() {
        IOException failure = assertThrows(IOException.class,
                () -> TomlLanguageParser.parseText("[runtime]\nprefix = 42\n"));

        assertTrue(failure.getMessage().contains("runtime.prefix"));
    }

    @Test
    public void sparseParsingIgnoresUnknownValuesButChecksKnownValues() throws Exception {
        String raw = "[runtime]\nprefix = \"&6Test\"\nretired = 42\n[legacy]\nitems = [1, 2]\n";

        assertEquals(Map.of("runtime.prefix", "&6Test"),
                TomlLanguageParser.parseText(raw, Set.of("runtime.prefix")));
        assertThrows(IOException.class,
                () -> TomlLanguageParser.parseText("[runtime]\nprefix = 42\n", Set.of("runtime.prefix")));
    }
}
