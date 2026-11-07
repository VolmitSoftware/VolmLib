package art.arcane.volmlib.util.config;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TomlCodecTest {
    @Test
    public void genericTablesPreserveLiteralDottedKeys() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("locale", "en_US");
        JsonObject plural = new JsonObject();
        JsonObject forms = new JsonObject();
        forms.addProperty("one", "{count} portal deleted");
        forms.addProperty("other", "{count} portals deleted");
        plural.add("command.admin.deleted_portals", forms);
        root.add("plural", plural);

        String content = TomlCodec.toToml(root);

        assertTrue(content.contains("[plural.\"command.admin.deleted_portals\"]"));
        assertEquals(root, TomlCodec.toJsonElement(content));
    }

    @Test
    public void genericTablesKeepLiteralAndHierarchicalPathsSeparate() throws IOException {
        JsonObject root = new JsonObject();
        JsonObject literal = new JsonObject();
        literal.addProperty("value", "literal");
        root.add("message.group", literal);
        JsonObject parent = new JsonObject();
        JsonObject nested = new JsonObject();
        nested.addProperty("value", "nested");
        parent.add("group", nested);
        root.add("message", parent);

        assertEquals(root, TomlCodec.toJsonElement(TomlCodec.toToml(root)));
    }

    @Test
    public void distinguishesLiteralBackslashesFromTomlEscapes() throws IOException {
        String source = "[values]\n"
                + "literal = \"path\\\\name\\\\tvalue\"\n"
                + "escaped = \"line\\nnext\\tcell\"\n"
                + "items = [\"a\\\\nb\", \"a\\nb\"]\n"
                + "\"key\\\\name\" = \"value\"\n"
                + "literal_single = 'a\\\\b'\n"
                + "literal_multiline = '''a\\\\b'''\n"
                + "trailing = \"path\\\\\"\n"
                + "private_use = \"\uE000\"\n";

        JsonObject values = TomlCodec.toJsonElement(source).getAsJsonObject().getAsJsonObject("values");

        assertEquals("path\\name\\tvalue", values.get("literal").getAsString());
        assertEquals("line\nnext\tcell", values.get("escaped").getAsString());
        assertEquals("a\\nb", values.getAsJsonArray("items").get(0).getAsString());
        assertEquals("a\nb", values.getAsJsonArray("items").get(1).getAsString());
        assertEquals("value", values.get("key\\name").getAsString());
        assertEquals("a\\\\b", values.get("literal_single").getAsString());
        assertEquals("a\\\\b", values.get("literal_multiline").getAsString());
        assertEquals("path\\", values.get("trailing").getAsString());
        assertEquals("\uE000", values.get("private_use").getAsString());
    }

    @Test
    public void compactEntriesDocumentFieldsOnceWithoutParentHeader() throws IOException {
        CompactConfig config = new CompactConfig();
        config.rules.put("b", new CompactRule(false, 5));

        String content = TomlCodec.toToml(config, "compact");

        assertEquals("# Configuration - compact\n"
                + "# This file is canonicalized on load; comments and new keys may update automatically.\n"
                + "\n"
                + "\n"
                + "# Rules by id.\n"
                + "# enabled: Allow the rule.\n"
                + "# cost: Fee.\n"
                + "[rules.a]\n"
                + "enabled = true\n"
                + "cost = 1\n"
                + "\n"
                + "[rules.b]\n"
                + "enabled = false\n"
                + "cost = 5\n", content);
        CompactConfig parsed = TomlCodec.fromToml(content, CompactConfig.class);
        assertEquals(5, parsed.rules.get("b").cost);
        assertFalse(parsed.rules.get("b").enabled);
    }

    @Test
    public void emptyCompactEntriesKeepTheDocumentedHeader() throws IOException {
        CompactConfig config = new CompactConfig();
        config.rules.clear();

        String content = TomlCodec.toToml(config, "compact");

        assertTrue(content.endsWith("# Rules by id.\n[rules]\n"));
    }

    public static final class CompactConfig {
        @ConfigDoc(value = "Rules by id.", compactEntries = true)
        public Map<String, CompactRule> rules = new TreeMap<>(Map.of("a", new CompactRule(true, 1)));
    }

    public static final class CompactRule {
        @ConfigDoc("Allow the rule.")
        public boolean enabled;
        @ConfigDoc("Fee.")
        public int cost;

        public CompactRule() {
        }

        CompactRule(boolean enabled, int cost) {
            this.enabled = enabled;
            this.cost = cost;
        }
    }
}
