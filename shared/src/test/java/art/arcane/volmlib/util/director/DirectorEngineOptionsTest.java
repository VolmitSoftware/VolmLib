package art.arcane.volmlib.util.director;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.handlers.StringHandlerBase;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

public class DirectorEngineOptionsTest {
    @Test
    public void defaultFactoryCreatesFreshRegistries() {
        DirectorRuntimeEngine first = DirectorEngineFactory.create(new TestCommands());
        DirectorRuntimeEngine second = DirectorEngineFactory.create(new TestCommands());

        assertNotSame(first.getParsers(), second.getParsers());
        assertNotSame(first.getContexts(), second.getContexts());
    }

    @Test
    public void optionsDefensivelyCopyLegacyHandlers() {
        DirectorParameterHandler<?> handler = new StringHandlerBase();
        List<DirectorParameterHandler<?>> mutable = new ArrayList<>();
        mutable.add(handler);

        DirectorEngineOptions options = DirectorEngineOptions.builder()
                .legacyHandlers(mutable)
                .build();
        mutable.clear();

        assertEquals(List.of(handler), options.getLegacyHandlers());
        assertThrows(UnsupportedOperationException.class, () -> options.getLegacyHandlers().clear());
    }

    @Director(name = "test")
    public static class TestCommands {
        @Director
        public void ping() {
        }
    }
}
