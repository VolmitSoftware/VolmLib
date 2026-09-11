package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DirectorHiddenCommandTest {
    @Test
    public void hiddenAliasExecutesWithoutAppearingInHelpOrCompletion() {
        Commands commands = new Commands();
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(commands);
        DirectorSender sender = new TestSender();
        DirectorMiniMenu.DirectorHelpPage root = DirectorMiniMenu.resolveHelp(engine, List.of(), 1).orElseThrow();

        assertEquals(1, root.totalPages());
        assertEquals("debug", root.entries().get(0).getDescriptor().getName());
        assertFalse(String.join("\n", DirectorMiniMenu.renderConsole(root, DirectorTextResolver.ENGLISH)).contains("version"));
        assertFalse(String.join("\n", DirectorMiniMenu.render(root, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH)).contains("version"));
        assertEquals(List.of("debug"), engine.tabComplete(new DirectorInvocation(sender, "test", List.of(""))));
        assertTrue(engine.tabComplete(new DirectorInvocation(sender, "test", List.of("ver"))).isEmpty());
        assertEquals(1, DirectorVisualCommand.createRoot(engine).getNodes().size());
        assertTrue(DirectorMiniMenu.resolveHelp(engine, List.of("version")).isEmpty());
        assertTrue(engine.execute(new DirectorInvocation(sender, "test", List.of("version"))).isSuccess());
        assertTrue(engine.execute(new DirectorInvocation(sender, "test", List.of("v"))).isSuccess());
        assertTrue(engine.execute(new DirectorInvocation(sender, "test", List.of("debug", "version"))).isSuccess());
        assertEquals(3, commands.debug.calls);

        DirectorMiniMenu.DirectorHelpPage debug = DirectorMiniMenu.resolveHelp(engine, List.of("debug")).orElseThrow();
        assertEquals("version", debug.entries().get(0).getDescriptor().getName());
        assertEquals(List.of("version"), engine.tabComplete(new DirectorInvocation(sender, "test", List.of("debug", ""))));
    }

    @Director(name = "test")
    public static class Commands {
        DebugCommands debug = new DebugCommands();

        @Director(name = "version", aliases = "v", hidden = true)
        public void version() {
            debug.version();
        }
    }

    @Director(name = "debug")
    public static class DebugCommands {
        private int calls;

        @Director(name = "version")
        public void version() {
            calls++;
        }
    }

    private static class TestSender implements DirectorSender {
        @Override
        public String getName() {
            return "Console";
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public void sendMessage(String message) {
        }
    }
}
