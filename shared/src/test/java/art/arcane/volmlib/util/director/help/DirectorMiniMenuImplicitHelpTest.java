package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectorMiniMenuImplicitHelpTest {
    private DirectorRuntimeEngine engine;

    @Before
    public void setUp() {
        engine = DirectorEngineFactory.create(new TestCommands());
    }

    private Optional<DirectorMiniMenu.DirectorHelpPage> resolve(List<String> args) {
        return DirectorMiniMenu.resolveHelp(engine, args, 17);
    }

    private List<String> names(List<DirectorRuntimeNode> nodes) {
        return nodes.stream().map(node -> node.getDescriptor().getName()).toList();
    }

    @Test
    public void bareCategoryTokenOpensThatCategoryHelpPage() {
        DirectorMiniMenu.DirectorHelpPage page = resolve(List.of("pregen")).orElseThrow();

        assertEquals("pregen", page.node().getDescriptor().getName());
        assertEquals(0, page.pageIndex());
        assertEquals(List.of("start", "stop", "tools"), names(page.entries()));
    }

    @Test
    public void bareNestedCategoryTokensOpenTheDeepestCategory() {
        DirectorMiniMenu.DirectorHelpPage page = resolve(List.of("pregen", "tools")).orElseThrow();

        assertEquals("tools", page.node().getDescriptor().getName());
        assertEquals("/test pregen tools", page.node().path());
    }

    @Test
    public void bareCategoryAliasOpensThatCategoryHelpPage() {
        DirectorMiniMenu.DirectorHelpPage page = resolve(List.of("pg")).orElseThrow();

        assertEquals("pregen", page.node().getDescriptor().getName());
    }

    @Test
    public void blankTokensDoNotBlockImplicitCategoryHelp() {
        DirectorMiniMenu.DirectorHelpPage page = resolve(List.of("pregen", "")).orElseThrow();

        assertEquals("pregen", page.node().getDescriptor().getName());
    }

    @Test
    public void invocableCommandIsLeftToTheEngine() {
        assertTrue(resolve(List.of("pregen", "start")).isEmpty());
        assertTrue(resolve(List.of("ping")).isEmpty());
    }

    @Test
    public void invocableCommandWithArgumentsIsLeftToTheEngine() {
        assertTrue(resolve(List.of("pregen", "start", "radius=512")).isEmpty());
    }

    @Test
    public void unmatchedTokenIsLeftToTheEngine() {
        assertTrue(resolve(List.of("qqqqqqqq")).isEmpty());
        assertTrue(resolve(List.of("pregen", "qqqqqqqq")).isEmpty());
    }

    @Test
    public void emptyArgumentsStillOpenTheRootHelpPage() {
        DirectorMiniMenu.DirectorHelpPage page = resolve(List.of()).orElseThrow();

        assertEquals("test", page.node().getDescriptor().getName());
        assertEquals(0, page.pageIndex());
    }

    @Test
    public void explicitHelpTokenOnInvocableCommandStillOpensTheParentPage() {
        DirectorMiniMenu.DirectorHelpPage page = resolve(List.of("pregen", "start", "help")).orElseThrow();

        assertEquals("pregen", page.node().getDescriptor().getName());
    }

    @Test
    public void explicitHelpPagingStillResolvesTheRequestedPage() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of("help=2"), 1).orElseThrow();

        assertEquals(1, page.pageIndex());
        assertEquals(3, page.totalPages());
    }

    @Test
    public void explicitHelpTokenOnCategoryStillOpensThatCategory() {
        DirectorMiniMenu.DirectorHelpPage page = resolve(List.of("pregen", "help")).orElseThrow();

        assertEquals("pregen", page.node().getDescriptor().getName());
    }

    @Director(name = "test", description = "Test commands")
    public static class TestCommands {
        PregenCommands pregen;

        @Director(name = "ping", description = "Check connectivity")
        public void ping() {
        }

        @Director(name = "reload", description = "Reload the plugin")
        public void reload() {
        }
    }

    @Director(name = "pregen", aliases = "pg", description = "Pregeneration commands")
    public static class PregenCommands {
        PregenToolCommands tools;

        @Director(name = "start", description = "Start a pregeneration task")
        public void start(
                @Param(name = "radius", description = "Radius in blocks")
                int radius,
                @Param(name = "pattern", description = "Traversal pattern", defaultValue = "spiral")
                String pattern
        ) {
        }

        @Director(name = "stop", description = "Stop the running pregeneration")
        public void stop() {
        }
    }

    @Director(name = "tools", description = "Pregeneration tools")
    public static class PregenToolCommands {
        @Director(name = "verify", description = "Verify generated regions")
        public void verify() {
        }
    }
}
