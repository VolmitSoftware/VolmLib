package art.arcane.volmlib.util.board;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CharacterizationBoardEntryTest {
    @Test
    public void emptyLineYieldsEmptyPrefixAndSuffix() {
        BoardEntry entry = BoardEntry.translateToEntry("");
        assertEquals("", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void completeLineUsesOneModernComponent() {
        String line = "§a§k1234567890123456789012345678901234567890§r complete";
        BoardEntry entry = BoardEntry.translateToEntry(line);

        assertEquals(line, entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void lineBreaksCollapseToSingleSpaces() {
        BoardEntry entry = BoardEntry.translateToEntry("A\r\nB\rC\nD");
        assertEquals("A B C D", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void validSurrogatePairsArePreservedWithoutTruncation() {
        String line = "123456789012345😀Z".repeat(8);
        BoardEntry entry = BoardEntry.translateToEntry(line);

        assertEquals(line, entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void unpairedSurrogatesAreRemoved() {
        BoardEntry entry = BoardEntry.translateToEntry("A\uD83DB\uDE00C");

        assertEquals("ABC", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }
}
