package art.arcane.volmlib.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class VolmLogTest {
    @Test
    public void warningIsBrandedAndPreservesTheFailure() {
        Logger logger = Logger.getLogger("VolmLib");
        List<LogRecord> records = new ArrayList<LogRecord>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        boolean parentHandlers = logger.getUseParentHandlers();
        Level previousLevel = logger.getLevel();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        IllegalStateException failure = new IllegalStateException("failure");
        try {
            VolmLog.warning("Test", "operation failed", failure);
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(parentHandlers);
        }

        assertEquals(1, records.size());
        assertEquals("[VolmLib/Test] operation failed", records.get(0).getMessage());
        assertSame(failure, records.get(0).getThrown());
    }

}
