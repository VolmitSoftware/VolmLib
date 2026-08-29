package art.arcane.volmlib.util.format;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColorFormatterTest {
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
