package art.arcane.volmlib.util.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class ConfigEditorDocumentTest {
    private static final String SOURCE = """
            # Main configuration
            language = "en_US" # Keep this comment
            enabled = true
            maximum = 10
            chance = 0.25
            worlds = ["world", "world_nether"]
            empty = []

            ["world.settings"]
            "1" = "literal numeric key"

            [[drops]]
            drop = "diamond"
            amount = 1

            [[drops]]
            drop = "emerald"
            amount = 2
            "custom.key" = "value"
            """;

    @Test
    public void browsesSectionsAndRepeatedRulesWithoutLosingLiteralKeys() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml(SOURCE);

        assertEquals(SOURCE, document.source());
        assertEquals(ConfigEditorDocument.Kind.TABLE_ARRAY, entry(document, "drops").kind());
        assertEquals(List.of("drops", "1"), document.entries(List.of("drops")).get(1).path());
        assertEquals("emerald", document.value(List.of("drops", "1", "drop")).getAsString());
        assertEquals("value", document.value(List.of("drops", "1", "custom.key")).getAsString());
        assertEquals("literal numeric key", document.value(List.of("world.settings", "1")).getAsString());
        assertThrows(IllegalArgumentException.class, () -> document.value(List.of("world", "settings", "1")));
        assertThrows(IllegalArgumentException.class, () -> document.value(List.of("drops", "-1")));
        assertThrows(IllegalArgumentException.class, () -> document.value(List.of("drops", "01")));
        assertThrows(IllegalArgumentException.class, () -> document.value(List.of("drops", "999999999999")));
        assertThrows(IllegalArgumentException.class, () -> document.entries(List.of("language")));
    }

    @Test
    public void snapshotAndEditsCannotBeMutatedThroughJsonReferences() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml(SOURCE);
        ConfigEditorDocument.Entry worlds = entry(document, "worlds");
        worlds.value().getAsJsonArray().add("not saved");
        document.value(List.of()).getAsJsonObject().addProperty("language", "changed");
        JsonArray replacement = new JsonArray();
        replacement.add("another_world");
        ConfigEditorDocument.Edit edit = document.edit(List.of("worlds"), replacement);
        replacement.add("external mutation");
        edit.value().getAsJsonArray().add("accessor mutation");
        edit.expected().getAsJsonArray().add("expected mutation");

        assertEquals(2, document.value(List.of("worlds")).getAsJsonArray().size());
        assertEquals(2, worlds.value().getAsJsonArray().size());
        assertEquals(2, edit.expected().getAsJsonArray().size());
        assertEquals(1, edit.value().getAsJsonArray().size());
        assertEquals("en_US", document.value(List.of("language")).getAsString());
        assertEquals(SOURCE, edit.original().source());
        assertThrows(UnsupportedOperationException.class, () -> edit.path().add("extra"));
    }

    @Test
    public void parsesValuesWithTheExistingTypeAndPreservesStringInput() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml(SOURCE);

        assertEquals("  text # literal  ", document.parseValue(List.of("language"), "  text # literal  ").getAsString());
        assertEquals(new JsonPrimitive(false), document.parseValue(List.of("enabled"), " FALSE "));
        assertEquals(new JsonPrimitive(Long.MAX_VALUE), document.parseValue(List.of("maximum"), Long.toString(Long.MAX_VALUE)));
        assertEquals(new JsonPrimitive(0.75D), document.parseValue(List.of("chance"), " 0.75 "));
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("enabled"), "yes"));
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("maximum"), "1.2"));
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("maximum"), "9223372036854775808"));
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("chance"), "NaN"));
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("chance"), "1e999"));
    }

    @Test
    public void parsesListsWithoutAllowingNewSettingsOrNestedTables() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml(SOURCE);
        JsonElement parsed = document.parseValue(List.of("worlds"), "[\"new world\", \"nether\"]");

        assertEquals("new world", parsed.getAsJsonArray().get(0).getAsString());
        assertEquals(0, document.parseValue(List.of("worlds"), "[]").getAsJsonArray().size());
        assertEquals("one", document.parseValue(List.of("empty"), "[\"one\"]").getAsJsonArray().get(0).getAsString());
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("worlds"), "[1]"));
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("worlds"), "[]\nenabled = false"));
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("worlds"), "[{name = \"x\"}]"));
        assertThrows(IllegalArgumentException.class, () -> document.parseValue(List.of("drops"), "[]"));
    }

    @Test
    public void editKeepsItsOriginalDocumentForStaleWriteDetection() throws IOException {
        ConfigEditorDocument document = ConfigEditorDocument.fromToml(SOURCE);
        ConfigEditorDocument.Edit edit = document.edit(List.of("drops", "1", "amount"), new JsonPrimitive(3L));
        ConfigEditorDocument current = ConfigEditorDocument.fromToml(SOURCE.replace("amount = 2", "amount = 4"));

        assertEquals(2, edit.expected().getAsInt());
        assertNotEquals(current.source(), edit.original().source());
        assertNotEquals(current.value(edit.path()), edit.expected());
        assertThrows(IllegalArgumentException.class, () -> document.edit(List.of("enabled"), new JsonPrimitive("false")));
        assertThrows(IllegalArgumentException.class, () -> document.edit(List.of("chance"), new JsonPrimitive(Double.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class, () -> document.edit(List.of("drops"), new JsonArray()));
    }

    private static ConfigEditorDocument.Entry entry(ConfigEditorDocument document, String name) {
        for (ConfigEditorDocument.Entry entry : document.entries(List.of())) {
            if (entry.name().equals(name)) {
                return entry;
            }
        }
        throw new AssertionError("Missing setting: " + name);
    }
}
