package art.arcane.volmlib.util.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class TomlArrayOfTablesTest {
    @Test
    public void genericTableArraysPreserveNestedObjectsAndEscapedPayloads() throws IOException {
        JsonElement original = JsonParser.parseString("""
                {
                  "name": "Starter",
                  "rewards": [
                    {"type": "ITEM", "lore": ["First", "Second"], "data": "item:\\n  type: STONE\\n", "meta": {"enabled": true}},
                    {"type": "COMMAND", "command": "say hello", "children": [{"chance": 25.0}, {"chance": 100.0}]}
                  ],
                  "named.tables": [{"quoted.key": "value"}, {}]
                }
                """);
        String toml = TomlCodec.toToml(original);
        assertTrue(toml.contains("[[rewards]]"));
        assertTrue(toml.contains("[[\"named.tables\"]]"));
        assertEquals(original, TomlCodec.toJsonElement(toml));
    }

    @Test
    public void reflectiveTableArraysSupportPojoArraysAndMapCollections() throws IOException {
        TableRoot original = new TableRoot();
        String toml = TomlCodec.toToml(original, "tables");
        TableRoot restored = TomlCodec.fromToml(toml, TableRoot.class);
        assertTrue(toml.contains("[[rewards]]"));
        assertEquals(original.rewards.get(0).name, restored.rewards.get(0).name);
        assertEquals(original.rewards.get(0).lore, restored.rewards.get(0).lore);
        assertEquals(original.tables.get(0), restored.tables.get(0));
        assertEquals(original.entries[0].name, restored.entries[0].name);
        assertEquals(original.groups.get("nested").get(0).name, restored.groups.get("nested").get(0).name);
    }

    @Test
    public void unsupportedMixedTableArraysFailWithoutRecursiveWrapping() {
        JsonElement mixed = JsonParser.parseString("{\"values\":[{\"name\":\"one\"},2]}");
        assertThrows(IllegalArgumentException.class, () -> TomlCodec.toToml(mixed));
    }

    public static final class TableRoot {
        private List<TableEntry> rewards = List.of(new TableEntry());
        private List<Map<String, String>> tables = List.of(Map.of("first", "value"));
        private TableEntry[] entries = {new TableEntry()};
        private Map<String, List<TableEntry>> groups = Map.of("nested", List.of(new TableEntry()));
    }

    public static final class TableEntry {
        private String name = "Reward";
        private List<String> lore = List.of("Line 1", "Line 2");
    }
}
