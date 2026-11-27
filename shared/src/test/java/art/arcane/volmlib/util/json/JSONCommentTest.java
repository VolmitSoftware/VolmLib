package art.arcane.volmlib.util.json;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class JSONCommentTest {
    @Test
    public void lineCommentsAreSkippedAroundAndBetweenTokens() {
        JSONObject object = new JSONObject("""
                // Folder-specific settings
                {
                  "a": 1, // trailing note
                  // whole-line note
                  "b": [2, // inside array
                        3]
                }
                // after root
                """);

        assertEquals(1, object.getInt("a"));
        assertEquals(2, object.getJSONArray("b").length());
        assertEquals(3, object.getJSONArray("b").getInt(1));
    }

    @Test
    public void blockCommentsAreSkippedIncludingMultiLineAndStarRuns() {
        JSONObject object = new JSONObject("""
                /* header
                 * spanning lines **/
                {"a": /* before value */ 1, /* between pairs */ "b" /* before colon */ : 2}
                """);

        assertEquals(1, object.getInt("a"));
        assertEquals(2, object.getInt("b"));
    }

    @Test
    public void commentMarkersInsideStringsAreKept() {
        JSONObject object = new JSONObject("{\"url\": \"https://volmit.com//x\", \"text\": \"/* not a comment */\"}");

        assertEquals("https://volmit.com//x", object.getString("url"));
        assertEquals("/* not a comment */", object.getString("text"));
    }

    @Test
    public void unclosedBlockCommentIsASyntaxError() {
        assertThrows(JSONException.class, () -> new JSONObject("{\"a\": 1 /* never closed"));
    }

    @Test
    public void loneSlashIsStillASyntaxError() {
        assertThrows(JSONException.class, () -> new JSONObject("{\"a\": 1 / \"b\": 2}"));
    }
}
