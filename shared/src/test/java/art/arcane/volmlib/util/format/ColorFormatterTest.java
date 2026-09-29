package art.arcane.volmlib.util.format;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColorFormatterTest {
    @Test
    public void lastColorsPreserveHexFormattingAndResetBoundaries() {
        assertEquals("\u00a7a\u00a7l", ColorFormatter.getLastColors("\u00a7cRed\u00a7aGreen\u00a7lBold"));
        assertEquals("\u00a7r\u00a7o", ColorFormatter.getLastColors("\u00a7aGreen\u00a7rReset\u00a7oItalic"));
        assertEquals("\u00a7x\u00a71\u00a72\u00a7a\u00a7B\u00a7e\u00a7F\u00a7l",
            ColorFormatter.getLastColors("\u00a7cOld\u00a7x\u00a71\u00a72\u00a7a\u00a7B\u00a7e\u00a7FHex\u00a7lBold"));
        assertEquals("", ColorFormatter.getLastColors("plain\u00a7Atext"));
        assertEquals("\u00a7X\u00a71\u00a72\u00a7a\u00a7B\u00a7e\u00a7F",
            ColorFormatter.getLastColors("\u00a7cOld\u00a7X\u00a71\u00a72\u00a7a\u00a7B\u00a7e\u00a7FHex"));
    }

    @Test
    public void translatesEverySupportedRgbSyntax() {
        String expected = "\u00a7x\u00a71\u00a72\u00a7a\u00a7b\u00a7e\u00a7fText";

        assertEquals(expected, ColorFormatter.translateColors("&#12ABefText"));
        assertEquals(expected, ColorFormatter.translateColors("&x12ABefText"));
        assertEquals(expected, ColorFormatter.translateColors("&x&1&2&A&B&e&fText"));
        assertEquals(expected, ColorFormatter.translateColors("[12ABef]Text"));
    }

    @Test
    public void malformedColorsRemainLiteral() {
        assertEquals("[12ZZef]Text &x12ZZef", ColorFormatter.translateColors("[12ZZef]Text &x12ZZef"));
    }

    @Test
    public void escapedColorPrefixesRemainLiteral() {
        assertEquals("&cRed [12ABef]Hex", ColorFormatter.translateColors("\\&cRed \\[12ABef]Hex"));
        assertEquals("C:\\temp &cRed", ColorFormatter.translateColors("C:\\temp \\&cRed"));
    }
}
