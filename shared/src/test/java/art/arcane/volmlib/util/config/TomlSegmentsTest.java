package art.arcane.volmlib.util.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TomlSegmentsTest {
    private static final String DOCUMENT = "title = \"root\" # [not.a.table]\n"
            + "[runtime]\n"
            + "plain = \"a ] b [\"\n"
            + "multiline = \"\"\"\n"
            + "[looks.like.a.table]\n"
            + "still the string \\\" no\n"
            + "\"\"\"\n"
            + "literal = '''\n"
            + "[also.inside]\n"
            + "'''\n"
            + "lore = [\n"
            + "  [\"first\"],\n"
            + "  [\"nested\"],\n"
            + "[\"line start\"],\n"
            + "]\n"
            + "[runtime.deeper] # comment\n"
            + "value = 1\n"
            + "[\"quoted ] key\"]\n"
            + "value = 'q'\n"
            + "[other]\n"
            + "inline = { list = [1, 2] }\n";

    @Test
    public void splitsAtTopLevelHeadersOnly() {
        List<String> segments = TomlSegments.split(DOCUMENT);

        assertEquals(5, segments.size());
        assertEquals("title = \"root\" # [not.a.table]\n", segments.get(0));
        assertEquals(true, segments.get(1).startsWith("[runtime]\n"));
        assertEquals(true, segments.get(1).contains("[looks.like.a.table]"));
        assertEquals(true, segments.get(2).startsWith("[runtime.deeper]"));
        assertEquals(true, segments.get(3).startsWith("[\"quoted ] key\"]"));
        assertEquals("[other]\ninline = { list = [1, 2] }\n", segments.get(4));
    }

    @Test
    public void segmentedParseMatchesTheWholeDocument() throws IOException {
        JsonElement parsed = TomlCodec.toJsonElement(DOCUMENT);

        JsonElement expected = JsonParser.parseString("{\"title\":\"root\",\"runtime\":{\"plain\":\"a ] b [\","
                + "\"multiline\":\"[looks.like.a.table]\\nstill the string \\\" no\\n\","
                + "\"literal\":\"[also.inside]\\n\",\"lore\":[[\"first\"],[\"nested\"],[\"line start\"]],"
                + "\"deeper\":{\"value\":1}},\"quoted ] key\":{\"value\":\"q\"},\"other\":{\"inline\":{\"list\":[1,2]}}}");
        assertEquals(expected, parsed);
        for (String segment : TomlSegments.split(DOCUMENT)) {
            TomlCodec.toJsonElement(segment);
        }
    }

    @Test
    public void arraysOfTablesStayInOneSegment() {
        assertEquals(1, TomlSegments.split("[[item]]\nname = 'a'\n[[item]]\nname = 'b'\n").size());
    }

    @Test
    public void repeatedHeadersAreStillRejected() {
        assertThrows(IOException.class, () -> TomlCodec.toJsonElement("[a]\nx = 1\n[b]\ny = 2\n[a]\nz = 3\n"));
    }

    @Test
    public void keysRedefinedByALaterTableAreStillRejected() {
        assertThrows(IOException.class, () -> TomlCodec.toJsonElement("[a]\nb = 1\n[a.b]\nc = 2\n"));
    }
}
