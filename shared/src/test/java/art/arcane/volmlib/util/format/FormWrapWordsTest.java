package art.arcane.volmlib.util.format;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FormWrapWordsTest {
    @Test
    public void wrapWordsBreaksOnWordBoundariesAndSoftCutsWhenItCannot() {
        String[][] cases = {
                {"one two three four", "9", "one two\nthree\nfour"},
                {"hello world", "5", "hello\nworld"},
                {"aaa bbb ccc", "3", "aaa\nbbb\nccc"},
                {"abcdefghij", "4", "abcd\nefgh\nij"},
                {"short", "9", "short"}
        };

        for (String[] wrapCase : cases) {
            String input = wrapCase[0];
            int width = Integer.parseInt(wrapCase[1]);
            assertEquals(input + " @ " + width, wrapCase[2], Form.wrapWords(input, width));
        }
    }

    @Test
    public void wrapWordsPrefixedBreaksAtActualSpace() {
        assertEquals("> one two\n> three\n> four", Form.wrapWordsPrefixed("one two three four", "> ", 9));
    }
}
