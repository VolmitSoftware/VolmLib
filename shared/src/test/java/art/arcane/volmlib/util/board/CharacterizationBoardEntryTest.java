package art.arcane.volmlib.util.board;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Characterization pins for {@link BoardEntry#translateToEntry(String)} — the 16-character
 * prefix/suffix split every board line goes through on both the Bukkit-team and packet paths.
 * These literals are the current on-the-wire mapping; a diffing refactor must reproduce them
 * byte for byte. Inputs use section codes because Board.update() color-translates lines
 * before splitting (see CharacterizationBoardUpdateTest for that ordering pin).
 */
public class CharacterizationBoardEntryTest {
    @Test
    public void emptyLineYieldsEmptyPrefixAndSuffix() {
        BoardEntry entry = BoardEntry.translateToEntry("");
        assertEquals("", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void shortLineIsPrefixOnly() {
        BoardEntry entry = BoardEntry.translateToEntry("Hello");
        assertEquals("Hello", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void sixteenCharacterLineIsPrefixOnly() {
        BoardEntry entry = BoardEntry.translateToEntry("1234567890123456");
        assertEquals("1234567890123456", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void trailingSectionSignIsRemovedFromSixteenCharacterLine() {
        BoardEntry entry = BoardEntry.translateToEntry("123456789012345§");
        assertEquals("123456789012345", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void seventeenCharacterPlainLineSplitsWithoutColorCarry() {
        BoardEntry entry = BoardEntry.translateToEntry("12345678901234567");
        assertEquals("1234567890123456", entry.getPrefix());
        assertEquals("7", entry.getSuffix());
    }

    @Test
    public void leadingColorCodeCarriesIntoSuffix() {
        BoardEntry entry = BoardEntry.translateToEntry("§a123456789012345678");
        assertEquals("§a12345678901234", entry.getPrefix());
        assertEquals("§a5678", entry.getSuffix());
    }

    @Test
    public void colorAndFormatCodesBothCarryIntoSuffix() {
        BoardEntry entry = BoardEntry.translateToEntry("§a§l12345678901234567890");
        assertEquals("§a§l123456789012", entry.getPrefix());
        assertEquals("§a§l34567890", entry.getSuffix());
    }

    @Test
    public void midLineColorOverrideCarriesLatestColor() {
        BoardEntry entry = BoardEntry.translateToEntry("12§a45678901234567890");
        assertEquals("12§a456789012345", entry.getPrefix());
        assertEquals("§a67890", entry.getSuffix());
    }

    @Test
    public void resetCodeCarriesResetIntoSuffix() {
        BoardEntry entry = BoardEntry.translateToEntry("§a1234§r56789012345678");
        assertEquals("§a1234§r56789012", entry.getPrefix());
        assertEquals("§r345678", entry.getSuffix());
    }

    @Test
    public void sectionSignAtSplitBoundaryMovesWholeCodeToSuffix() {
        BoardEntry entry = BoardEntry.translateToEntry("123456789012345§a12");
        assertEquals("123456789012345", entry.getPrefix());
        assertEquals("§a12", entry.getSuffix());
    }

    @Test
    public void trailingSectionSignIsRemovedAfterPrefixBoundary() {
        BoardEntry entry = BoardEntry.translateToEntry("1234567890123456§");
        assertEquals("1234567890123456", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void overflowBeyondThirtyTwoCharactersIsDropped() {
        BoardEntry entry = BoardEntry.translateToEntry("1234567890123456789012345678901234567890");
        assertEquals("1234567890123456", entry.getPrefix());
        assertEquals("7890123456789012", entry.getSuffix());
    }

    @Test
    public void hexRunCarriesWholeSequenceAndEatsTheSuffixBudget() {
        // ChatColor.getLastColors understands legacy hex runs: the full 14-char sequence is
        // carried into the suffix, leaving only 2 of the 16 suffix chars for visible text —
        // "EF" is silently dropped. Current behavior, pinned as-is.
        BoardEntry entry = BoardEntry.translateToEntry("§x§1§2§3§4§5§6ABCDEF");
        assertEquals("§x§1§2§3§4§5§6AB", entry.getPrefix());
        assertEquals("§x§1§2§3§4§5§6CD", entry.getSuffix());
    }

    @Test
    public void lineBreaksCollapseToSingleSpaces() {
        BoardEntry entry = BoardEntry.translateToEntry("A\r\nB\rC\nD");
        assertEquals("A B C D", entry.getPrefix());
        assertEquals("", entry.getSuffix());
    }

    @Test
    public void surrogatePairAtPrefixBoundaryMovesWhollyToSuffix() {
        BoardEntry entry = BoardEntry.translateToEntry("123456789012345😀Z");
        assertEquals("123456789012345", entry.getPrefix());
        assertEquals("😀Z", entry.getSuffix());
    }

    @Test
    public void surrogatePairAtLineLimitIsDroppedWhole() {
        BoardEntry entry = BoardEntry.translateToEntry("1234567890123456123456789012345😀Z");
        assertEquals("1234567890123456", entry.getPrefix());
        assertEquals("123456789012345", entry.getSuffix());
    }

    @Test
    public void completeHexColorAtPrefixBoundaryMovesWhollyToSuffix() {
        BoardEntry entry = BoardEntry.translateToEntry("1234567890§x§1§2§3§4§5§6Z");
        assertEquals("1234567890", entry.getPrefix());
        assertEquals("§x§1§2§3§4§5§6Z", entry.getSuffix());
    }

    @Test
    public void completeHexColorAtLineLimitIsDroppedWhole() {
        BoardEntry entry = BoardEntry.translateToEntry("123456789012345612345§x§1§2§3§4§5§6Z");
        assertEquals("1234567890123456", entry.getPrefix());
        assertEquals("12345", entry.getSuffix());
    }
}
