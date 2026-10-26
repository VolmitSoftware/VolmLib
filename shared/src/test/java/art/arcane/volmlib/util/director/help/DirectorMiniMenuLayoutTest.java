package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorNodeDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectorMiniMenuLayoutTest {
    @Test
    public void rootPageFillsTwentyChatLines() {
        DirectorRuntimeEngine engine = rootEngine(18);

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();
        List<String> rendered = render(page);

        assertEquals(new DirectorMiniMenu.ContentPage(1, 1, 0, 18, 18), window(page));
        assertEquals(DirectorMiniMenu.CHAT_LINE_COUNT, rendered.size());
    }

    @Test
    public void rootPageSpillsPastEighteenEntries() {
        DirectorRuntimeEngine engine = rootEngine(19);

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();
        DirectorMiniMenu.DirectorHelpPage second = DirectorMiniMenu.resolveHelp(engine, List.of("help=2")).orElseThrow();

        assertEquals(19, page.entries().size());
        assertEquals(new DirectorMiniMenu.ContentPage(1, 2, 0, 18, 19), window(page));
        assertEquals(new DirectorMiniMenu.ContentPage(2, 2, 18, 19, 19), window(second));
        assertTrue(render(page).get(19).contains("<click:run_command:/test help=2>"));
        assertTrue(render(second).get(2).contains("<click:run_command:/test help=1>"));
    }

    @Test
    public void submenuGivesARowToItsBackLine() {
        DirectorRuntimeNode root = group("test", null);
        DirectorRuntimeNode tools = group("tools", root);
        root.addChild(tools);
        addChildren(tools, 18);
        DirectorRuntimeEngine engine = engine(root);

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of("tools")).orElseThrow();
        List<String> rendered = render(page);

        assertEquals(new DirectorMiniMenu.ContentPage(1, 2, 0, 17, 18), window(page));
        assertEquals(DirectorMiniMenu.CHAT_LINE_COUNT, rendered.size());
        assertTrue(rendered.get(1).contains("〈 Back"));
    }

    @Test
    public void wrappedEntriesCountEveryChatRow() {
        DirectorRuntimeNode root = group("test", null);
        for (int index = 1; index <= 12; index++) {
            root.addChild(group("command%02d".formatted(index) + "x".repeat(40), root));
        }
        DirectorRuntimeEngine engine = engine(root);

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();
        List<String> rendered = render(page);

        assertEquals(2, DirectorMiniMenu.chatRows(rendered.get(1)));
        assertEquals(new DirectorMiniMenu.ContentPage(1, 2, 0, 9, 12), window(page));
        assertEquals(11, rendered.size());
    }

    @Test
    public void filteredEntriesArePagedAfterFiltering() {
        DirectorRuntimeEngine engine = rootEngine(30);

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of("help=2")).orElseThrow();
        DirectorMiniMenu.DirectorHelpPage visible = page.withEntries(page.entries().subList(0, 10));

        assertEquals(new DirectorMiniMenu.ContentPage(1, 1, 0, 10, 10), window(visible));
        assertEquals(12, render(visible).size());
    }

    @Test
    public void consoleStillListsAllEntriesWithoutPlayerPagination() {
        DirectorRuntimeEngine engine = rootEngine(20);
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();

        List<String> rendered = DirectorMiniMenu.renderConsole(page, DirectorTextResolver.ENGLISH);

        assertEquals(20, page.entries().size());
        assertEquals(21, rendered.size());
        assertEquals("command20 - Category of Commands", rendered.get(20));
    }

    private static List<String> render(DirectorMiniMenu.DirectorHelpPage page) {
        return DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
    }

    private static DirectorMiniMenu.ContentPage window(DirectorMiniMenu.DirectorHelpPage page) {
        return DirectorMiniMenu.helpWindow(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
    }

    private static DirectorRuntimeEngine rootEngine(int childCount) {
        DirectorRuntimeNode root = group("test", null);
        addChildren(root, childCount);
        return engine(root);
    }

    private static DirectorRuntimeEngine engine(DirectorRuntimeNode root) {
        return new DirectorRuntimeEngine(root, DirectorEngineOptions.builder().build());
    }

    private static void addChildren(DirectorRuntimeNode parent, int childCount) {
        for (int index = 1; index <= childCount; index++) {
            parent.addChild(group("command%02d".formatted(index), parent));
        }
    }

    private static DirectorRuntimeNode group(String name, DirectorRuntimeNode parent) {
        DirectorNodeDescriptor descriptor = new DirectorNodeDescriptor(
                name,
                "",
                "Test command group",
                List.of(),
                DirectorOrigin.BOTH,
                DirectorExecutionMode.SYNC,
                true,
                false,
                List.of()
        );
        return new DirectorRuntimeNode(descriptor, parent, new Object(), null, List.of());
    }
}
