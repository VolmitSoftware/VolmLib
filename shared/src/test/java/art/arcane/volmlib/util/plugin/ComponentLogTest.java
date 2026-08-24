package art.arcane.volmlib.util.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ComponentLogTest {
    @Test
    public void paperComponentLoggerReceivesLegacyColors() {
        Plugin plugin = mock(Plugin.class);
        ComponentLogger componentLogger = mock(ComponentLogger.class);
        Logger fallback = logger(new ArrayList<LogRecord>());
        when(plugin.getComponentLogger()).thenReturn(componentLogger);

        ComponentLog.logLegacy(plugin, fallback, "[Test] ", Level.WARNING, "\u00a7eWarning", null);

        org.mockito.ArgumentCaptor<Component> component = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(componentLogger).warn(component.capture());
        assertEquals("\u00a7eWarning", LegacyComponentSerializer.legacySection().serialize(component.getValue()));
    }

    @Test
    public void julFallbackUsesPlainTextAndRetainsFailure() {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = logger(records);
        Logger fallback = logger(new ArrayList<LogRecord>());
        Plugin plugin = mock(Plugin.class);
        ComponentLogger componentLogger = mock(ComponentLogger.class);
        IllegalStateException failure = new IllegalStateException("broken");
        when(plugin.getComponentLogger()).thenReturn(null);
        when(plugin.getLogger()).thenReturn(logger);

        ComponentLog.logLegacy(plugin, fallback, "[Test] ", Level.SEVERE, "\u00a7cFailure", failure);

        assertEquals(1, records.size());
        assertEquals(Level.SEVERE, records.get(0).getLevel());
        assertEquals("Failure", records.get(0).getMessage());
        assertFalse(records.get(0).getMessage().contains("\u00a7"));
        assertSame(failure, records.get(0).getThrown());
        verifyNoInteractions(componentLogger);
    }

    @Test
    public void missingPluginUsesPrefixedEmptyFallbackMessage() {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = logger(records);

        ComponentLog.log(null, logger, "[Test] ", null, ComponentText.empty(), null);

        assertEquals(1, records.size());
        assertEquals(Level.INFO, records.get(0).getLevel());
        assertEquals("[Test] ", records.get(0).getMessage());
    }

    private static Logger logger(List<LogRecord> records) {
        Logger logger = Logger.getLogger("ComponentLogTest-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record != null && isLoggable(record)) {
                    records.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        return logger;
    }
}
