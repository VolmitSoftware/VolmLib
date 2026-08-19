package art.arcane.volmlib.util.config;

import art.arcane.volmlib.util.io.IO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigFileSupportTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void createsMissingConfigFromFallbackAndReloadsIt() throws IOException {
        File file = new File(temp.getRoot(), "config.toml");
        RecordingIo io = new RecordingIo(temp.getRoot());

        Sample created = ConfigFileSupport.load(io, file, null, Sample.class, new Sample(), true, "core-config", null);

        assertTrue(file.exists());
        assertEquals("gloss", created.name);

        Sample loaded = ConfigFileSupport.load(io, file, null, Sample.class, new Sample(), true, "core-config", null);

        assertEquals(created.name, loaded.name);
        assertEquals(created.threshold, loaded.threshold, 0D);
        assertTrue(io.warnings.isEmpty());
    }

    @Test
    public void rewritesInvalidConfigWithFallbackAndWarns() throws IOException {
        File file = new File(temp.getRoot(), "config.toml");
        IO.writeAll(file, "key =");
        RecordingIo io = new RecordingIo(temp.getRoot());

        Sample loaded = ConfigFileSupport.load(io, file, null, Sample.class, new Sample(), true, "core-config", null);

        assertEquals("gloss", loaded.name);
        assertFalse(io.warnings.isEmpty());
        assertTrue(IO.readAll(file).contains("name = \"gloss\""));
    }

    @Test
    public void migratesLegacyJsonIntoCanonicalTomlAndDeletesLegacy() throws IOException {
        File canonical = new File(temp.getRoot(), "config.toml");
        File legacy = new File(temp.getRoot(), "config.json");
        IO.writeAll(legacy, "{\"name\":\"custom\",\"threshold\":9.0}");
        RecordingIo io = new RecordingIo(temp.getRoot());

        Sample loaded = ConfigFileSupport.load(io, canonical, legacy, Sample.class, new Sample(), true, "core-config", null);

        assertEquals("custom", loaded.name);
        assertEquals(9.0D, loaded.threshold, 0D);
        assertTrue(canonical.exists());
        assertFalse(legacy.exists());
        assertFalse(io.infos.isEmpty());
    }

    public static class Sample {
        private String name = "gloss";
        private double threshold = 3.5D;
    }

    private static final class RecordingIo implements ConfigIo {
        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final File dataFolder;

        private RecordingIo(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        @Override
        public void info(String message) {
            infos.add(message);
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public File dataFolder() {
            return dataFolder;
        }
    }
}
