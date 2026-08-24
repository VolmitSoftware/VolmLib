package art.arcane.volmlib.util;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void firstPartyRuntimeDoesNotWriteRawConsoleOutput() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations = new ArrayList<String>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/util/json/") || normalized.contains("/util/math/")) {
                    continue;
                }
                String source = Files.readString(path);
                if (source.contains("System.out") || source.contains("System.err")
                        || source.contains(".printStackTrace(") || source.contains("Throwable::printStackTrace")) {
                    violations.add(normalized);
                }
            }
        }

        assertTrue("raw console output in " + violations, violations.isEmpty());
    }
}
