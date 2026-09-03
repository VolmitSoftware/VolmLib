package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonElement;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TomlLanguageEditorTest {
    @Test
    public void updatesQuotedDottedKeysAtTheRootAndInsideTables() throws IOException {
        String raw = "\"command.version\" = \"Old root\"\n\n[menu]\n\"status.message\" = \"Old nested\"\n";
        TomlLanguageEditor.EditResult root = TomlLanguageEditor.upsert(raw,
                "command.version", new TextValue("New root"));
        TomlLanguageEditor.EditResult nested = TomlLanguageEditor.upsert(root.content(),
                "menu.status.message", new TextValue("New nested"));
        JsonElement parsed = TomlCodec.toJsonElement(nested.content());
        assertEquals("New root", parsed.getAsJsonObject().get("command.version").getAsString());
        assertFalse(parsed.getAsJsonObject().has("command"));
        assertEquals("New nested", parsed.getAsJsonObject().getAsJsonObject("menu")
                .get("status.message").getAsString());
        assertFalse(parsed.getAsJsonObject().getAsJsonObject("menu").has("status"));
        TomlLanguageEditor.EditResult removed = TomlLanguageEditor.remove(nested.content(), "command.version");
        assertFalse(TomlCodec.toJsonElement(removed.content()).getAsJsonObject().has("command.version"));
    }

    @Test
    public void writesLineListsAndPluralTablesWithoutFlatteningTheirShape() throws IOException {
        TomlLanguageEditor.EditResult lines = TomlLanguageEditor.upsert("# Messages\n",
                "menu.lore", new LinesValue(List.of("First {name}", "Second")));
        TomlLanguageEditor.EditResult plural = TomlLanguageEditor.upsert(lines.content(),
                "count.players", new PluralValue(Map.of("one", "One player", "other", "{count} players")));
        JsonElement parsed = TomlCodec.toJsonElement(plural.content());
        assertEquals("First {name}", parsed.getAsJsonObject().getAsJsonObject("menu")
                .getAsJsonArray("lore").get(0).getAsString());
        assertEquals(2, parsed.getAsJsonObject().getAsJsonObject("menu").getAsJsonArray("lore").size());
        assertEquals("{count} players", parsed.getAsJsonObject().getAsJsonObject("count")
                .getAsJsonObject("players").get("other").getAsString());
        assertTrue(plural.content().startsWith("# Messages\n"));
    }

    @Test
    public void upsertsTextWhilePreservingUnknownValuesSemantically() throws IOException {
        String raw = "[runtime]\nprefix = \"Old\"\n\n[unknown]\nenabled = true\nports = [1, 2]\n";

        TomlLanguageEditor.EditResult result = TomlLanguageEditor.upsertText(
                raw, "runtime.prefix", "\\&dNew \"value\"\nline");
        JsonElement parsed = TomlCodec.toJsonElement(result.content());

        assertEquals("\\&dNew \"value\"\nline",
                parsed.getAsJsonObject().getAsJsonObject("runtime").get("prefix").getAsString());
        assertTrue(parsed.getAsJsonObject().getAsJsonObject("unknown").get("enabled").getAsBoolean());
        assertEquals(2, parsed.getAsJsonObject().getAsJsonObject("unknown").getAsJsonArray("ports").size());
        assertFalse(result.empty());
    }

    @Test
    public void createsAndPrunesNestedTables() throws IOException {
        TomlLanguageEditor.EditResult created = TomlLanguageEditor.upsertText(
                "", "portal.notice.created", "Created");
        assertEquals("Created", TomlLanguageParser.parseText(created.content()).get("portal.notice.created"));
        assertEquals("[portal.notice]\ncreated = \"Created\"\n", created.content());

        TomlLanguageEditor.EditResult removed = TomlLanguageEditor.remove(
                created.content(), "portal.notice.created");
        assertTrue(removed.empty());
    }

    @Test
    public void canonicalizesLegacyEmptyParentsAndIndentedAssignments() throws IOException {
        String legacy = "[command]\n\n[command.description]\n    config = \"Old\"\n";

        TomlLanguageEditor.EditResult result = TomlLanguageEditor.upsertText(
                legacy, "command.description.config", "New");

        assertEquals("New", TomlLanguageParser.parseText(result.content()).get("command.description.config"));
        assertTrue(result.content().contains("[command.description]\nconfig = \"New\""));
        assertFalse(result.content().contains("[command]\n"));
        assertFalse(result.content().contains("    config ="));
    }

    @Test
    public void preservesTheLeadingLanguageCommentBlock() throws IOException {
        String raw = "# Language: en_US\n# Keep placeholders such as {prefix}.\n\n[runtime]\nprefix = \"Old\"\n";

        TomlLanguageEditor.EditResult result = TomlLanguageEditor.upsertText(
                raw, "runtime.prefix", "New");

        assertTrue(result.content().startsWith(
                "# Language: en_US\n# Keep placeholders such as {prefix}.\n\n"));
        assertEquals("New", TomlLanguageParser.parseText(result.content()).get("runtime.prefix"));
    }

    @Test
    public void preservesExplicitEmptyAndMixedTables() throws IOException {
        String raw = "[empty]\n\n[mixed]\nlabel = \"kept\"\n\n[mixed.child]\nenabled = true\n";

        TomlLanguageEditor.EditResult result = TomlLanguageEditor.upsertText(
                raw, "command.description.config", "New");

        assertTrue(result.content().contains("[empty]\n"));
        assertTrue(result.content().contains("[mixed]\nlabel = \"kept\""));
        assertTrue(result.content().contains("[mixed.child]\nenabled = true"));
        assertFalse(result.content().contains("  label ="));
        assertFalse(result.content().contains("  enabled ="));
    }

    @Test
    public void rejectsTableAndScalarCollisions() {
        assertThrows(IOException.class, () -> TomlLanguageEditor.upsertText(
                "portal = \"scalar\"\n", "portal.notice.created", "Created"));
    }
}
