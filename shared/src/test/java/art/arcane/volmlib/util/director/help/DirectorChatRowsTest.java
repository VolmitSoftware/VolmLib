package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import org.junit.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectorChatRowsTest {
    @Test
    public void blankLinesTakeNoRows() {
        assertEquals(0, DirectorMiniMenu.chatRows(""));
        assertEquals(0, DirectorMiniMenu.chatRows("   "));
        assertEquals(0, DirectorMiniMenu.chatRows(null));
    }

    @Test
    public void shortLinesTakeOneRowWhateverTheirMarkup() {
        assertEquals(1, DirectorMiniMenu.chatRows("<hover:show_text:'" + "x".repeat(400) + "'><gradient:#000000:#ffffff>Short</gradient></hover>"));
    }

    @Test
    public void newlinesStartRows() {
        assertEquals(3, DirectorMiniMenu.chatRows("first\nsecond\nthird"));
    }

    @Test
    public void wordsWrapAtTheChatWidth() {
        String word = "abcde";
        String fits = String.join(" ", IntStream.range(0, 9).mapToObj(index -> word).toList());
        String wraps = fits + " " + word;

        assertEquals(1, DirectorMiniMenu.chatRows(fits));
        assertEquals(2, DirectorMiniMenu.chatRows(wraps));
    }

    @Test
    public void longWordsBreakAcrossRows() {
        assertEquals(1, DirectorMiniMenu.chatRows("a".repeat(53)));
        assertEquals(2, DirectorMiniMenu.chatRows("a".repeat(54)));
        assertEquals(3, DirectorMiniMenu.chatRows("a".repeat(107)));
    }

    @Test
    public void boldTextIsWider() {
        assertEquals(1, DirectorMiniMenu.chatRows("a".repeat(46)));
        assertEquals(2, DirectorMiniMenu.chatRows("<bold>" + "a".repeat(46) + "</bold>"));
    }

    @Test
    public void contentPagesFitWrappedEntriesIntoTwentyLines() {
        List<String> entries = IntStream.rangeClosed(1, 10)
                .mapToObj(index -> "Portal " + index + "\nWorld world\nAt 0 64 0")
                .toList();
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/portals", "/portals", "/root", entries, "", 1, DirectorMiniMenu.MAX_ENTRIES_PER_PAGE);

        List<String> rendered = DirectorMiniMenu.renderContent(menu, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
        int rows = rendered.stream().mapToInt(DirectorMiniMenu::chatRows).sum();

        assertEquals(new DirectorMiniMenu.ContentPage(1, 2, 0, 5, 10), menu.page());
        assertEquals(18, rows);
        assertTrue(rendered.get(rendered.size() - 1).contains("<click:run_command:/portals page=2>"));
    }

    @Test
    public void pageBarsStayOnOneLineWithLongLabels() {
        DirectorTextResolver longLabel = (key, arguments) -> key.id().equals(DirectorHelpMessages.PAGE.id())
                ? "Seitennummer"
                : DirectorTextResolver.ENGLISH.resolve(key, arguments);
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(400, 10, 18);

        String english = DirectorMiniMenu.paginationBar(page, "/test", DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
        String translated = DirectorMiniMenu.paginationBar(page, "/test", DirectorMiniMenu.Theme.reactBlue(), longLabel);

        assertEquals(1, DirectorMiniMenu.chatRows(english));
        assertEquals(1, DirectorMiniMenu.chatRows(translated));
        assertTrue(translated.contains("Seitennummer 11 ❭"));
    }

    @Test
    public void contentPagesStillHonorTheirEntryLimit() {
        List<String> entries = IntStream.rangeClosed(1, 10).mapToObj(index -> "Entry " + index).toList();

        assertEquals(new DirectorMiniMenu.ContentPage(2, 3, 4, 8, 10), DirectorMiniMenu.paginate(entries, 2, 4, 2));
    }

    @Test
    public void anEntryTallerThanTheChatGetsAPageOfItsOwn() {
        List<String> entries = List.of("one", "row\n".repeat(30) + "end", "three");

        assertEquals(new DirectorMiniMenu.ContentPage(2, 3, 1, 2, 3), DirectorMiniMenu.paginate(entries, 2, 18, 2));
    }

    @Test
    public void emptyContentHasOnePage() {
        assertEquals(new DirectorMiniMenu.ContentPage(1, 1, 0, 0, 0), DirectorMiniMenu.paginate(List.of(), 5, 18, 2));
    }
}
