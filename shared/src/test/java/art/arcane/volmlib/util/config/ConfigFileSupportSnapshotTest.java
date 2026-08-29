package art.arcane.volmlib.util.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ConfigFileSupportSnapshotTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesTomlWithoutReadingTheSourceFile() throws Exception {
        File source = new File(temporaryFolder.getRoot(), "missing.toml");

        SnapshotConfig parsed = ConfigFileSupport.parseSnapshot(
                "name=\"stable\"\ncount=4\n",
                source,
                SnapshotConfig.class,
                config -> config.count = Math.max(10, config.count)
        );

        assertEquals("stable", parsed.name);
        assertEquals(10, parsed.count);
    }

    @Test(expected = IOException.class)
    public void rejectsNullParserResults() throws Exception {
        File source = new File(temporaryFolder.getRoot(), "null.json");
        ConfigFileSupport.parseSnapshot("null", source, SnapshotConfig.class, null);
    }

    public static final class SnapshotConfig {
        private String name = "default";
        private int count;
    }
}
