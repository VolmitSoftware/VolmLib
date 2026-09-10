package art.arcane.volmlib.util.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TomlDocumentEditorTest {
    @Test
    public void changesOnlyTheRequestedValueAndRetainsCommentsAndLineEndings() throws IOException {
        String source = "# Main settings\r\n\"language\"\t= 'en_US'  # Used by the server\r\n"
                + "metrics = true\r\n\r\n# First drop\r\n[[drops]]\r\nitem = 'diamond'\r\n";

        String updated = TomlDocumentEditor.set(source, List.of("language"), new JsonPrimitive("de_DE"));

        assertEquals(source.replace("'en_US'", "\"de_DE\""), updated);
        assertEquals(source.replace("'en_US'", "\"en_US\""),
                TomlDocumentEditor.set(updated, List.of("language"), new JsonPrimitive("en_US")));
    }

    @Test
    public void ignoresApparentSettingsInsideBothMultilineStringForms() throws IOException {
        String source = "banner = \"\"\"\n[veins]\nlanguage = 'inside basic'\n[[drops]]\n\"\"\"\n"
                + "example = '''\nlanguage = \"inside literal\"\n# keep this text\n'''\n"
                + "language = \"en_US\" # current\n"
                + "commands = ['say # literal', \"say \\\"quoted\\\"\"]\n";

        String updated = TomlDocumentEditor.set(source, List.of("language"), new JsonPrimitive("de_DE"));

        assertEquals(source.replace("\"en_US\"", "\"de_DE\""), updated);
    }

    @Test
    public void addsAMissingRootSettingWithoutChangingNestedSettings() throws IOException {
        String source = "# Configuration\r\n# Keep this header\r\n\r\n[custom]\r\nlanguage = 'nested'\r\n";

        String updated = TomlDocumentEditor.set(source, List.of("language"), new JsonPrimitive("fr_FR"));

        assertEquals(source.replace("[custom]", "\"language\" = \"fr_FR\"\r\n[custom]"), updated);
        assertEquals("nested", TomlCodec.toJsonElement(updated).getAsJsonObject()
                .getAsJsonObject("custom").get("language").getAsString());
    }

    @Test
    public void addsToAnEmptyDocumentOrAnUnterminatedHeaderComment() throws IOException {
        assertEquals("\"language\" = \"de_DE\"\n",
                TomlDocumentEditor.set("", List.of("language"), new JsonPrimitive("de_DE")));
        assertEquals("# Configuration\n\"language\" = \"de_DE\"\n",
                TomlDocumentEditor.set("# Configuration", List.of("language"), new JsonPrimitive("de_DE")));
    }

    @Test
    public void rootInsertionRetainsIndentationOnTheFirstExistingStatement() throws IOException {
        String source = "# Configuration\n\n  metrics = true # keep alignment\n";

        String updated = TomlDocumentEditor.set(source, List.of("language"), new JsonPrimitive("de_DE"));

        assertEquals("# Configuration\n\n\"language\" = \"de_DE\"\n  metrics = true # keep alignment\n", updated);
    }

    @Test
    public void editsTheSelectedArrayTableAndItsChildTables() throws IOException {
        String source = "# Example drop\n[[drops]]\nitem = 'diamond'\nchance = 0.5 # first\n"
                + "[drops.sound]\nvolume = 1.0\n"
                + "[[drops]]\nitem = 'emerald'\nchance = 0.75 # second\n"
                + "[drops.sound]\nvolume = 2.0\n";

        String updated = TomlDocumentEditor.set(source, List.of("drops", "1", "chance"), new JsonPrimitive(0.25));
        updated = TomlDocumentEditor.set(updated, List.of("drops", "1", "sound", "volume"), new JsonPrimitive(0.8));

        assertEquals(source.replace("0.75", "0.25").replace("volume = 2.0", "volume = 0.8"), updated);
    }

    @Test
    public void nestedArrayTableIndicesBelongToTheirParentEntry() throws IOException {
        String source = "[[regions]]\nname = 'first'\n[[regions.drops]]\nchance = 0.1\n"
                + "[[regions.drops]]\nchance = 0.2\n"
                + "[[regions]]\nname = 'second'\n[[regions.drops]]\nchance = 0.3\n";

        String updated = TomlDocumentEditor.set(source,
                List.of("regions", "1", "drops", "0", "chance"), new JsonPrimitive(0.7));

        assertEquals(source.replace("0.3", "0.7"), updated);
    }

    @Test
    public void quotedKeysKeepLiteralDotsEscapesAndNumericNames() throws IOException {
        String source = "[custom.\"world.v2\"]\n\"1\" = 'numeric'\n\"message.with.dots\" = 'unchanged'\n"
                + "\"unicode\\u0020key\" = 'before'\n";

        String updated = TomlDocumentEditor.set(source,
                List.of("custom", "world.v2", "unicode key"), new JsonPrimitive("after"));
        updated = TomlDocumentEditor.set(updated,
                List.of("custom", "world.v2", "1"), new JsonPrimitive("named key"));

        assertEquals(source.replace("'before'", "\"after\"").replace("'numeric'", "\"named key\""), updated);
    }

    @Test
    public void editsAnInlineTableMemberWithoutReformattingItsNeighbors() throws IOException {
        String source = "values = { first = 1, \"second.key\" = [ 'a', 'b' ], third = true } # inline\n";

        String updated = TomlDocumentEditor.set(source,
                List.of("values", "second.key", "1"), new JsonPrimitive("new"));

        assertEquals(source.replace("'b'", "\"new\""), updated);
    }

    @Test
    public void editsAnArrayMemberWithoutRemovingItsInternalComments() throws IOException {
        String source = "worlds = [\n    'world', # Default world\n    'mines', # Mining world\n]\n";

        String updated = TomlDocumentEditor.set(source, List.of("worlds", "1"), new JsonPrimitive("resources"));

        assertEquals(source.replace("'mines'", "\"resources\""), updated);
    }

    @Test
    public void replacesAPrimitiveArrayAndPreservesItsSurroundingDocumentation() throws IOException {
        String source = "# Tools allowed to find drops\nallowed_tools = ['pickaxe'] # any material tier\n";
        JsonArray tools = new JsonArray();
        tools.add("pickaxe");
        tools.add("shovel");

        String updated = TomlDocumentEditor.set(source, List.of("allowed_tools"), tools);

        assertEquals(source.replace("['pickaxe']", "['pickaxe', \"shovel\"]"), updated);
    }

    @Test
    public void wholeArrayEditsKeepInternalCommentsWhenChangingItsLength() throws IOException {
        String source = "# World selection\nworlds = [\n  'world', # Main world\n  'mines', # Mining world\n]\n";
        JsonArray shortened = new JsonArray();
        shortened.add("resources");

        String updated = TomlDocumentEditor.set(source, List.of("worlds"), shortened);

        assertEquals(source.replace("'world'", "\"resources\"").replace("'mines',", ""), updated);
        JsonArray expanded = new JsonArray();
        expanded.add("resources");
        expanded.add("mines");
        expanded.add("extra");
        updated = TomlDocumentEditor.set(updated, List.of("worlds"), expanded);
        assertEquals(expanded, TomlCodec.toJsonElement(updated).getAsJsonObject().get("worlds"));
        assertTrue(updated.contains("# Main world"));
        assertTrue(updated.contains("# Mining world"));
        assertTrue(updated.startsWith("# World selection\n"));
    }

    @Test
    public void clearingAnArrayRetainsItsCommentsAndAllowsLaterAdditions() throws IOException {
        String source = "values = [\n  'first', # Keep the first note\n  'second' # Keep the second note\n]\n";

        String cleared = TomlDocumentEditor.set(source, List.of("values"), new JsonArray());

        assertEquals(source.replace("'first',", "").replace("'second'", ""), cleared);
        JsonArray next = new JsonArray();
        next.add("third");
        String updated = TomlDocumentEditor.set(cleared, List.of("values"), next);
        assertEquals(next, TomlCodec.toJsonElement(updated).getAsJsonObject().get("values"));
        assertTrue(updated.contains("# Keep the first note"));
        assertTrue(updated.contains("# Keep the second note"));
    }

    @Test
    public void appendedValuesPutCommasBeforeAnExistingInlineComment() throws IOException {
        String source = "values = [\r\n  'first' # Keep this note\r\n]\r\n";
        JsonArray next = new JsonArray();
        next.add("changed");
        next.add("second");

        String updated = TomlDocumentEditor.set(source, List.of("values"), next);

        assertTrue(updated.contains("\"changed\", # Keep this note\r\n"));
        assertEquals(next, TomlCodec.toJsonElement(updated).getAsJsonObject().get("values"));
    }

    @Test
    public void unchangedValuesKeepTheirOriginalSpellingAndFormat() throws IOException {
        String source = "language = 'en_US' # keep single quotes\nlimit = 1_000\n";

        assertEquals(source, TomlDocumentEditor.set(source, List.of("language"), new JsonPrimitive("en_US")));
        assertEquals(source, TomlDocumentEditor.set(source, List.of("limit"), new JsonPrimitive(1000)));
    }

    @Test
    public void rejectsMalformedDocumentsMissingNestedPathsAndStructuralReplacement() {
        assertThrows(IOException.class,
                () -> TomlDocumentEditor.set("language = \"unfinished\n", List.of("language"), new JsonPrimitive("de_DE")));
        assertThrows(IOException.class,
                () -> TomlDocumentEditor.set("[veins]\nmaximum = 3\n", List.of("veins", "missing"), new JsonPrimitive(2)));
        assertThrows(IllegalArgumentException.class,
                () -> TomlDocumentEditor.set("[veins]\nmaximum = 3\n", List.of("veins"), new JsonObject()));
    }

    @Test
    public void validatesReplacementStringEscapesSemantically() throws IOException {
        String source = "command = 'say old' # reward\n";
        String command = "say \"C:\\mines\\bonus\" #1\nnext line";

        String updated = TomlDocumentEditor.set(source, List.of("command"), new JsonPrimitive(command));

        assertEquals(command, TomlCodec.toJsonElement(updated).getAsJsonObject().get("command").getAsString());
        assertTrue(updated.endsWith(" # reward\n"));
    }
}
