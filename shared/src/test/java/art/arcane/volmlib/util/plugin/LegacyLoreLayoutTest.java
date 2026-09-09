package art.arcane.volmlib.util.plugin;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class LegacyLoreLayoutTest {
    @Test
    public void wrapsAtWordsAndBreaksLongIdentifiers() {
        assertEquals(List.of("one two", "three"), LegacyLoreLayout.wrap("one two three", 7));
        assertEquals(List.of("123456", "7890"), LegacyLoreLayout.wrap("1234567890", 6));
    }

    @Test
    public void carriesRgbAndDecorationWithoutCountingCodes() {
        String color = "§x§e§8§9§b§6§2";
        assertEquals(List.of(color + "§lalpha", color + "§lbeta"),
                LegacyLoreLayout.wrap(color + "§lalpha beta", 5));
        assertEquals(List.of("§aalpha", "§abeta§r", "gamma"),
                LegacyLoreLayout.wrap("§aalpha beta§r gamma", 5));
    }

    @Test
    public void treatsCjkAsWideAndKeepsCombiningCharactersTogether() {
        assertEquals(List.of("中文測", "試內容"), LegacyLoreLayout.wrap("中文測試內容", 6));
        assertEquals(List.of("a\u0301bc", "de"), LegacyLoreLayout.wrap("a\u0301bcde", 3));
        assertEquals(List.of("😀😀", "😀"), LegacyLoreLayout.wrap("😀😀😀", 4));
    }

    @Test
    public void preservesExplicitEmptyLinesAndStylesAcrossParagraphs() {
        assertEquals(List.of("§aone", "§a", "§atwo"), LegacyLoreLayout.wrap("§aone\n\ntwo", 8));
    }
}
