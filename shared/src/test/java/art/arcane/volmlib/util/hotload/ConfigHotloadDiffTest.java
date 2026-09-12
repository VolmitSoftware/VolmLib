package art.arcane.volmlib.util.hotload;

import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigHotloadDiffTest {
    @Test
    public void ignoresSetOrderAndDuplicateMembers() {
        assertTrue(diff("{types:['COW','CHICKEN','BLAZE']}",
                "{types:['BLAZE','COW','CHICKEN','COW']}").isEmpty());
    }

    @Test
    public void reportsOnlyAddedAndRemovedMembers() {
        List<ConfigHotloadEngine.DiffEntry> changes = diff("{types:['BLAZE','CHICKEN']}",
                "{types:['COW','BLAZE']}");

        assertEquals(List.of(
                new ConfigHotloadEngine.DiffEntry("$.types[\"CHICKEN\"]", "\"CHICKEN\"", ConfigHotloadEngine.REMOVED),
                new ConfigHotloadEngine.DiffEntry("$.types[\"COW\"]", ConfigHotloadEngine.MISSING, "\"COW\"")
        ), changes);
    }

    @Test
    public void handlesTransitionsToAndFromEmptySetsWithoutPhantomArrayChanges() {
        assertEquals(1, diff("{types:[]}", "{types:['COW']}").size());
        assertEquals(1, diff("{types:['COW']}", "{types:[]}").size());
        assertEquals(1, diff("{types:['COW']}", "{other:true}").stream()
                .filter(entry -> entry.key().startsWith("$.types")).count());
        assertEquals(1, diff("{other:true}", "{other:true,types:['COW']}").size());
        assertEquals(List.of(new ConfigHotloadEngine.DiffEntry("$.types", ConfigHotloadEngine.MISSING, "[]")),
                diff("{other:true}", "{other:true,types:[]}"));
        assertEquals(List.of(new ConfigHotloadEngine.DiffEntry("$.types", "[]", ConfigHotloadEngine.REMOVED)),
                diff("{other:true,types:[]}", "{other:true}"));
    }

    @Test
    public void preservesOrderedListAndUnknownArraySemantics() {
        List<ConfigHotloadEngine.DiffEntry> changes = diff("{ordered:['A','B']}", "{ordered:['B','A']}");
        assertEquals(2, changes.size());
        assertEquals("$.ordered[0]", changes.get(0).key());
        assertEquals(2, ConfigHotloadEngine.computeStructuredDiff(
                "{types:['A','B']}", "{types:['B','A']}", JsonParser::parseString).size());
    }

    @Test
    public void resolvesInheritedRenamedNestedAndMapSetFields() {
        assertTrue(diff("{inherited:['A','B'],renamed:['A','B'],nested:{types:['A','B']},groups:{x:['A','B']}}",
                "{inherited:['B','A'],renamed:['B','A'],nested:{types:['B','A']},groups:{x:['B','A']}}").isEmpty());
    }

    @Test
    public void ignoresNestedObjectFieldOrderWithinSetMembersButPreservesTheirArrayOrder() {
        assertTrue(diff("{objects:[{nested:{a:1,b:2},ordered:[{a:1,b:2},3]}]}",
                "{objects:[{ordered:[{b:2,a:1},3],nested:{b:2,a:1}}]}").isEmpty());
        assertEquals(2, diff("{objects:[{ordered:[1,2]}]}", "{objects:[{ordered:[2,1]}]}").size());
    }

    @Test
    public void preservesLiteralMarkupAndSentinelLikeStringValues() {
        List<ConfigHotloadEngine.DiffEntry> changes = diff("{ordered:['<missing>']}", "{ordered:['<red>&a</red>']}");
        assertEquals("\"<missing>\"", changes.get(0).oldValue());
        assertEquals("\"<red>&a</red>\"", changes.get(0).newValue());
    }

    private static List<ConfigHotloadEngine.DiffEntry> diff(String before, String after) {
        return ConfigHotloadEngine.computeStructuredDiff(before, after, JsonParser::parseString, Config.class);
    }

    private static class BaseConfig {
        private Set<String> inherited;
    }

    private static class Config extends BaseConfig {
        private Set<String> types;
        private List<String> ordered;
        @SerializedName("renamed")
        private Set<String> alternate;
        private NestedConfig nested;
        private Map<String, Set<String>> groups;
        private Set<Map<String, Object>> objects;
    }

    private static class NestedConfig {
        private Set<String> types;
    }
}
