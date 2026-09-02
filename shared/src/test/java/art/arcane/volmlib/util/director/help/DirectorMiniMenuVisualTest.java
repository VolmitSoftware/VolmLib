package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DirectorMiniMenuVisualTest {
    private DirectorRuntimeEngine engine;

    @Before
    public void setUp() {
        engine = DirectorEngineFactory.create(new TestCommands());
    }

    private List<String> render(List<String> args, int pageSize) {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, args, pageSize).orElseThrow();
        return DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
    }

    @Test
    public void headerCentersTitleInsideStrikethroughBanner() {
        String header = render(List.of(), 10).get(0);

        assertTrue(header.startsWith("<font:minecraft:uniform><strikethrough><gradient:#001933:#1f4f80>["));
        assertTrue(header.contains(" ".repeat(31) + "((("));
        assertTrue(header.contains("<gradient:#003366:#00BFFF>/test</gradient>"));
        assertTrue(header.contains(")))" + " ".repeat(31) + "]"));
        assertTrue(header.endsWith("]</gradient></strikethrough></font>"));
    }

    @Test
    public void headerOmitsPageIndicatorsWhenPaginated() {
        String header = render(List.of("help=2"), 1).get(0);

        assertTrue(header.contains("<gradient:#003366:#00BFFF>/test</gradient>"));
        assertFalse(header.contains("2/3"));
    }

    @Test
    public void invocableEntryShowsArrowNameAndParameterChips() {
        String rendered = String.join("\n", render(List.of(), 10));

        assertTrue(rendered.contains("<click:suggest_command:/test create >"));
        assertTrue(rendered.contains("<gradient:#003366:#00BFFF>create</gradient></click>"));
        assertTrue(rendered.contains("<#7d93b2>⇀</#7d93b2>"));
        assertTrue(rendered.contains("<#ff6666>[</#ff6666><gradient:#003366:#00BFFF>name</gradient><#ff6666>]</#ff6666>"));
        assertTrue(rendered.contains("<#7d93b2>⊰</#7d93b2><gradient:#003366:#00BFFF>type</gradient><#7d93b2>⊱</#7d93b2>"));
    }

    @Test
    public void groupEntryOpensItsOwnHelpAndShowsCategorySuffix() {
        String rendered = String.join("\n", render(List.of(), 10));

        assertTrue(rendered.contains("<click:run_command:/test empty help=1>"));
        assertTrue(rendered.contains("- Category of Commands"));
    }

    @Test
    public void entryHoverContainsUsageAndExampleInvocation() {
        String rendered = String.join("\n", render(List.of(), 10));

        assertTrue(rendered.contains("Hover over the parameters to learn more."));
        assertTrue(rendered.contains("✦"));
        assertTrue(rendered.contains("/test create name=\\\\<String> type=overworld"));
    }

    @Test
    public void middlePageFooterEmbedsBothPageButtonsInTheBar() {
        List<String> lines = render(List.of("help=2"), 1);
        String footer = lines.get(lines.size() - 1);

        assertTrue(footer.contains("〈 Page 1"));
        assertTrue(footer.contains("<click:run_command:/test help=1>"));
        assertTrue(footer.contains("Page 3 ❭"));
        assertTrue(footer.contains("<click:run_command:/test help=3>"));
        assertTrue(footer.contains(" ".repeat(55)));
        assertFalse(footer.contains(" ".repeat(56)));
    }

    @Test
    public void firstPageFooterOmitsThePreviousButton() {
        List<String> lines = render(List.of("help=1"), 1);
        String footer = lines.get(lines.size() - 1);

        assertFalse(footer.contains("〈 Page"));
        assertTrue(footer.contains("Page 2 ❭"));
        assertTrue(footer.contains(" ".repeat(65)));
    }

    @Test
    public void singlePageFooterIsAPlainBar() {
        List<String> lines = render(List.of(), 10);
        String footer = lines.get(lines.size() - 1);

        assertEquals(
                "<font:minecraft:uniform><strikethrough><gradient:#1f4f80:#001933>"
                        + " ".repeat(75)
                        + "</gradient></strikethrough></font>",
                footer
        );
    }

    @Test
    public void contentPagesUseTheSameBannerAndBidirectionalFooter() {
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(40, 2, 15);
        DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.irisGreen();
        String header = DirectorMiniMenu.banner("/iris examples", theme);
        String footer = DirectorMiniMenu.paginationBar(
                page,
                "/iris examples",
                theme,
                DirectorTextResolver.ENGLISH
        );

        assertTrue(header.contains("<gradient:#5ef288:#32bfad>/iris examples</gradient>"));
        assertFalse(header.contains("2/3"));
        assertTrue(footer.contains("〈 Page 1"));
        assertTrue(footer.contains("<click:run_command:/iris examples page=1>"));
        assertTrue(footer.contains("Page 3 ❭"));
        assertTrue(footer.contains("<click:run_command:/iris examples page=3>"));
    }

    @Test
    public void paginatedBannerOverloadRetainsItsPageTitleContract() {
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(40, 2, 15);
        DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.irisGreen();

        String header = DirectorMiniMenu.banner("/gloss preview", page, theme);

        assertTrue(header.contains("/gloss preview {2/3}"));
        assertEquals("/gloss preview {2/3}", page.title("/gloss preview"));
        assertEquals("/gloss preview", DirectorMiniMenu.paginate(4, 1, 15).title("/gloss preview"));
    }

    @Test
    public void longContentMenuHeadersUseTheFooterWidthBudget() {
        String header = DirectorMiniMenu.banner(
                "/shapedportals config language en_US",
                DirectorMiniMenu.Theme.reactBlue()
        );

        assertTrue(header.contains(">[" + " ".repeat(10) + "(((</gradient>"));
        assertTrue(header.contains(">)))" + " ".repeat(10) + "]</gradient>"));
    }

    @Test
    public void contentPagesClampRequestsAndExposeTheirSlice() {
        assertEquals(
                new DirectorMiniMenu.ContentPage(2, 2, 15, 25, 25),
                DirectorMiniMenu.paginate(25, 3, 15)
        );
        assertEquals(
                new DirectorMiniMenu.ContentPage(1, 1, 0, 0, 0),
                DirectorMiniMenu.paginate(0, 0, 15)
        );
    }

    @Test
    public void contentMenusUseTheHelpPageBudgetAndNavigationSkeleton() {
        List<String> entries = IntStream.rangeClosed(1, 20)
                .mapToObj(index -> "Portal " + index)
                .toList();
        DirectorMiniMenu.ContentMenu firstMenu = new DirectorMiniMenu.ContentMenu(
                "/shapedportals portals", "/shapedportals portals", entries, "No portals", 1,
                DirectorMiniMenu.MAX_ENTRIES_PER_PAGE);
        DirectorMiniMenu.ContentMenu secondMenu = new DirectorMiniMenu.ContentMenu(
                "/shapedportals portals", "/shapedportals portals", entries, "No portals", 2,
                DirectorMiniMenu.MAX_ENTRIES_PER_PAGE);
        DirectorMiniMenu.ContentMenu clampedMenu = new DirectorMiniMenu.ContentMenu(
                "/shapedportals portals", "/shapedportals portals", entries, "No portals", 99,
                DirectorMiniMenu.MAX_ENTRIES_PER_PAGE);

        List<String> first = DirectorMiniMenu.renderContent(
                firstMenu, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
        List<String> second = DirectorMiniMenu.renderContent(
                secondMenu, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);

        assertEquals(DirectorMiniMenu.MAX_ENTRIES_PER_PAGE + 2, first.size());
        assertTrue(first.get(0).contains("/shapedportals portals"));
        assertFalse(first.get(0).contains("1/2"));
        assertEquals("Portal 19", first.get(DirectorMiniMenu.MAX_ENTRIES_PER_PAGE));
        assertTrue(first.get(first.size() - 1)
                .contains("<click:run_command:/shapedportals portals page=2>"));
        assertEquals(3, second.size());
        assertTrue(second.get(0).contains("/shapedportals portals"));
        assertFalse(second.get(0).contains("2/2"));
        assertEquals("Portal 20", second.get(1));
        assertTrue(second.get(2).contains("<click:run_command:/shapedportals portals page=1>"));
        assertEquals(2, clampedMenu.page().page());
        assertEquals(21, DirectorMiniMenu.renderContentConsole(firstMenu).size());
        assertEquals("Portal 20", DirectorMiniMenu.renderContentConsole(firstMenu).get(20));
    }

    @Test
    public void contentMenusRenderTheirEmptyStateInsideTheHelpSkeleton() {
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/shapedportals portals", "/shapedportals portals", List.of(), "No portals", 1,
                DirectorMiniMenu.MAX_ENTRIES_PER_PAGE);

        List<String> rendered = DirectorMiniMenu.renderContent(
                menu, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);

        assertEquals(3, rendered.size());
        assertEquals("No portals", rendered.get(1));
    }

    @Test
    public void backslashesInAuthorTextStayLiteralInsideHovers() {
        DirectorRuntimeEngine localEngine = DirectorEngineFactory.create(new BackslashCommands());
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(localEngine, List.of(), 10).orElseThrow();
        String rendered = String.join("\n", DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH));

        assertTrue(rendered.contains("Path C:\\\\\\\\test"));
        assertTrue(rendered.contains("dir=C:\\\\\\\\worlds"));
    }

    @Director(name = "test", description = "Test commands")
    public static class BackslashCommands {
        @Director(name = "locate", description = "Path C:\\test lookup")
        public void locate(
                @Param(name = "dir", description = "Target directory", defaultValue = "C:\\worlds")
                String dir
        ) {
        }
    }

    @Director(name = "test", description = "Test commands", descriptionKey = "test.description")
    public static class TestCommands {
        EmptyCommands empty;

        @Director(name = "create", aliases = "make", description = "Create a world", descriptionKey = "test.create.description")
        public void create(
                @Param(name = "name", description = "World name", descriptionKey = "test.create.name.description")
                String name,
                @Param(name = "type", description = "Pack type", descriptionKey = "test.create.type.description", defaultValue = "overworld")
                String type
        ) {
        }

        @Director(name = "ping", description = "Check connectivity", descriptionKey = "test.ping.description")
        public void ping() {
        }
    }

    @Director(name = "empty", aliases = "pinggroup", description = "Empty group", descriptionKey = "test.empty.description")
    public static class EmptyCommands {
    }
}
