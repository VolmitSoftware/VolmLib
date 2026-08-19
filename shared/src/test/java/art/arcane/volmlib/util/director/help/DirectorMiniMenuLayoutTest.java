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
    public void rootPageFitsSeventeenEntriesWithinTheNineteenLineBudget() {
        DirectorRuntimeEngine engine = rootEngine(18);

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();
        List<String> rendered = DirectorMiniMenu.render(
                page,
                DirectorMiniMenu.Theme.reactBlue(),
                DirectorTextResolver.ENGLISH
        );

        assertEquals(17, page.entries().size());
        assertEquals(2, page.totalPages());
        assertEquals(19, DirectorMiniMenu.MENU_LINE_COUNT);
        assertEquals(DirectorMiniMenu.MENU_LINE_COUNT, rendered.size());
    }

    @Test
    public void submenuReservesOneLineForItsBackRow() {
        DirectorRuntimeNode root = group("test", null);
        DirectorRuntimeNode tools = group("tools", root);
        root.addChild(tools);
        addChildren(tools, 17);
        DirectorRuntimeEngine engine = engine(root);

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of("tools")).orElseThrow();
        List<String> rendered = DirectorMiniMenu.render(
                page,
                DirectorMiniMenu.Theme.reactBlue(),
                DirectorTextResolver.ENGLISH
        );

        assertEquals(16, page.entries().size());
        assertEquals(2, page.totalPages());
        assertEquals(DirectorMiniMenu.MENU_LINE_COUNT, rendered.size());
        assertTrue(rendered.get(1).contains("〈 Back"));
    }

    @Test
    public void consoleStillListsAllEntriesWithoutPlayerPagination() {
        DirectorRuntimeEngine engine = rootEngine(20);
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();

        List<String> rendered = DirectorMiniMenu.renderConsole(page, DirectorTextResolver.ENGLISH);

        assertEquals(17, page.entries().size());
        assertEquals(21, rendered.size());
        assertEquals("command20 - Category of Commands", rendered.get(20));
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
                List.of()
        );
        return new DirectorRuntimeNode(descriptor, parent, new Object(), null, List.of());
    }
}
