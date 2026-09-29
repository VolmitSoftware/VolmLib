package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TomlLanguageWriterTest {
    @Test
    public void roundTripsGroupedTextLinesPluralsAndCollidingParents() throws Exception {
        MessageCatalog catalog = MessageCatalog.of("en_US",
                TextKey.of("command.help.web", "Editor"),
                TextKey.of("command.help.web.open", "Open {name}"),
                LinesKey.of("command.help.web.lore", "Line {name}"),
                PluralKey.of("command.help.web.count", "count", Map.of("other", "{count} editors")),
                LinesKey.of("menu.item.lore", "First {name}", "Second"),
                PluralKey.of("status.players", "count", Map.of("one", "{count} player", "other", "{count} players")));
        LinkedHashMap<String, MessageValue> values = new LinkedHashMap<>();
        for (MessageKey key : catalog.keys()) {
            values.put(key.id(), key.englishValue());
        }

        String rendered = TomlLanguageWriter.render(values, List.of("Header"));

        assertTrue(rendered.contains("[command.help]\nweb = \"Editor\"\n\"web.open\" = \"Open {name}\""));
        assertTrue(rendered.contains("[menu.item]\nlore = [\"First {name}\", \"Second\"]"));
        assertTrue(rendered.contains("[status.players]"));
        assertFalse(rendered.contains("\"command.help.web.open\" ="));
        assertEquals(values, TomlLanguageParser.parseValidValues(rendered, catalog));
    }

    @Test
    public void emptyHeaderLinesHaveNoTrailingWhitespace() {
        assertEquals("# Header\n#\n\n[message]\ntext = \"Value\"\n",
                TomlLanguageWriter.renderText(Map.of("message.text", "Value"), List.of("Header", "")));
    }

    @Test
    public void parsesChildTranslationsWithoutAParentTranslation() throws Exception {
        MessageCatalog catalog = MessageCatalog.of("en_US",
                TextKey.of("command.help.web", "Editor"),
                TextKey.of("command.help.web.open", "Open"));

        assertEquals(Map.of("command.help.web.open", new TextValue("Öffnen")),
                TomlLanguageParser.parseValidValues("[command.help.web]\nopen = \"Öffnen\"\n", catalog));
    }

    @Test
    public void preservesValidSiblingsOfInvalidListsAndPluralForms() throws Exception {
        MessageCatalog catalog = MessageCatalog.of("en_US",
                TextKey.of("status.label", "Status"),
                LinesKey.of("status.lines", "First {name}", "Second"),
                PluralKey.of("status.players", "count", Map.of("other", "{count} players")));
        String source = "[status]\nlabel = \"État\"\nlines = [\"Missing variable\", \"Second\"]\n"
                + "[status.players]\nother = \"Wrong {variable}\"\n";

        assertEquals(Map.of("status.label", new TextValue("État")),
                TomlLanguageParser.parseValidValues(source, catalog));
    }

    @Test
    public void ambiguousSemanticKeysUseEnglishWithoutDiscardingOtherMessages() throws Exception {
        MessageCatalog catalog = MessageCatalog.of("en_US",
                TextKey.of("status.label", "Status"), TextKey.of("status.good", "Good"));
        String source = "\"status.label\" = \"First\"\n[status]\nlabel = \"Second\"\ngood = \"Gut\"\n";

        assertEquals(Map.of("status.good", new TextValue("Gut")),
                TomlLanguageParser.parseValidValues(source, catalog));
    }
}
