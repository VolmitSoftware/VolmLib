package art.arcane.volmlib.util.config;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TomlCodecCommentsTest {
    private static final String GENERATED_TOML = "# Configuration - comments\n"
            + "# This file is canonicalized on load; comments and new keys may update automatically.\n"
            + "\n"
            + "# Blocks bad keys.\n"
            + "# Effect: Overridden by a system property.\n"
            + "strict = false\n"
            + "# Controls Debug.\n"
            + "debug = false\n"
            + "\n"
            + "# Settings for Plain.\n"
            + "[plain]\n"
            + "# Controls Depth in the plain section.\n"
            + "depth = 3\n"
            + "\n"
            + "# Tuning knobs.\n"
            + "[tuned]\n"
            + "# Controls Depth in the tuned section.\n"
            + "depth = 3\n";

    @Test
    public void generatedCommentsStayTheDefault() {
        CommentedConfig config = new CommentedConfig();

        assertEquals(GENERATED_TOML, TomlCodec.toToml(config, "comments"));
        assertEquals(GENERATED_TOML, TomlCodec.toToml(config, "comments", ConfigExposePolicy.ALL, ConfigComments.GENERATED));
        assertEquals(GENERATED_TOML, TomlCodec.toToml(config, "comments", ConfigExposePolicy.ALL, null));
    }

    @Test
    public void annotatedOnlyCommentsWriteJustTheAnnotations() throws IOException {
        CommentedConfig config = new CommentedConfig();
        config.debug = true;
        config.tuned.depth = 7;

        String content = TomlCodec.toToml(config, "comments", ConfigExposePolicy.ALL, ConfigComments.ANNOTATED_ONLY);

        assertEquals("# Configuration - comments\n"
                + "# This file is canonicalized on load; comments and new keys may update automatically.\n"
                + "\n"
                + "# Blocks bad keys.\n"
                + "# Effect: Overridden by a system property.\n"
                + "strict = false\n"
                + "debug = true\n"
                + "\n"
                + "[plain]\n"
                + "depth = 3\n"
                + "\n"
                + "# Tuning knobs.\n"
                + "[tuned]\n"
                + "depth = 7\n", content);
        CommentedConfig parsed = TomlCodec.fromToml(content, CommentedConfig.class);
        assertTrue(parsed.debug);
        assertEquals(3, parsed.plain.depth);
        assertEquals(7, parsed.tuned.depth);
    }

    public static final class CommentedConfig {
        @ConfigDoc(value = "Blocks bad keys.", impact = "Overridden by a system property.")
        public boolean strict = false;
        public boolean debug = false;
        public Section plain = new Section();
        @ConfigDoc("Tuning knobs.")
        public Section tuned = new Section();
    }

    public static final class Section {
        public int depth = 3;
    }
}
