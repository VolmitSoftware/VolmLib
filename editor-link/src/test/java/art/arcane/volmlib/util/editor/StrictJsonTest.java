package art.arcane.volmlib.util.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrictJsonTest {
    private static String nested(int arrays) { return "{\"a\":" + "[".repeat(arrays) + "]".repeat(arrays) + "}"; }

    @Test
    void object_normalObject_parses() throws IOException {
        JsonObject parsed = StrictJson.object(" {\"a\": 1, \"b\": [true, null, \"x\"], \"c\": {\"d\": 1.5}, \"e\": 12345678901234567890} ");
        assertEquals(1, parsed.get("a").getAsInt());
        assertTrue(parsed.getAsJsonArray("b").get(0).getAsBoolean());
        assertEquals(JsonNull.INSTANCE, parsed.getAsJsonArray("b").get(1));
        assertEquals("x", parsed.getAsJsonArray("b").get(2).getAsString());
        assertEquals(1.5, parsed.getAsJsonObject("c").get("d").getAsDouble());
        assertEquals("12345678901234567890", parsed.get("e").getAsBigDecimal().toPlainString());
        assertEquals(List.of("a", "b", "c", "e"), List.copyOf(parsed.keySet()));
    }

    @Test
    void object_duplicateKeys_refused() {
        IOException top = assertThrows(IOException.class, () -> StrictJson.object("{\"a\":1,\"a\":2}"));
        assertTrue(top.getMessage().contains("duplicate key"), top.getMessage());
        assertThrows(IOException.class, () -> StrictJson.object("{\"x\":{\"a\":1,\"a\":1}}"));
        assertThrows(IOException.class, () -> StrictJson.object("{\"x\":[{\"b\":null,\"b\":null}]}"));
    }

    @Test
    void object_lenientSyntax_refused() {
        for (String text : List.of("{a:1}", "{'a':1}", "{\"a\":'x'}", "{\"a\":1 /* note */}", "{\"a\":1} // note", "# note\n{\"a\":1}", "{\"a\":1,}",
                "{\"a\":NaN}", "{\"a\":1;\"b\":2}", "{\"a\"=1}", "{\"a\":[1,]}", "{\"a\":unquoted}"))
            assertThrows(IOException.class, () -> StrictJson.object(text), text);
    }

    @Test
    void object_trailingData_refused() {
        for (String text : List.of("{\"a\":1}{\"b\":2}", "{\"a\":1} x", "{\"a\":1}]", "{\"a\":1} 1"))
            assertThrows(IOException.class, () -> StrictJson.object(text), text);
    }

    @Test
    void object_depthOverLimit_refused() throws IOException {
        assertEquals(StrictJson.MAX_DEPTH, 64);
        StrictJson.object(nested(64));
        IOException deep = assertThrows(IOException.class, () -> StrictJson.object(nested(65)));
        assertTrue(deep.getMessage().contains("nesting exceeds 64"), deep.getMessage());
    }

    @Test
    void object_nonObjectRoots_refused() {
        for (String text : List.of("[]", "[{}]", "1", "\"x\"", "null", "true", "", "   "))
            assertThrows(IOException.class, () -> StrictJson.object(text), text);
    }
}
