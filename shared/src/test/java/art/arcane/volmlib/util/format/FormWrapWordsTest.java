package art.arcane.volmlib.util.format;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FormWrapWordsTest {
    @Test
    public void wrapWordsBreaksAtActualSpaceInsteadOfHardCuttingMidWord() {
        assertEquals("one two\nthree\nfour", Form.wrapWords("one two three four", 9));
    }

    @Test
    public void wrapWordsBreaksSingleSpacePair() {
        assertEquals("hello\nworld", Form.wrapWords("hello world", 5));
    }

    @Test
    public void wrapWordsBreaksExactWidthWords() {
        assertEquals("aaa\nbbb\nccc", Form.wrapWords("aaa bbb ccc", 3));
    }

    @Test
    public void wrapWordsSoftCutsUnbreakableWord() {
        assertEquals("abcd\nefgh\nij", Form.wrapWords("abcdefghij", 4));
    }

    @Test
    public void wrapWordsLeavesShortStringUntouched() {
        assertEquals("short", Form.wrapWords("short", 9));
    }

    @Test
    public void wrapWordsPrefixedBreaksAtActualSpace() {
        assertEquals("> one two\n> three\n> four", Form.wrapWordsPrefixed("one two three four", "> ", 9));
    }
}
