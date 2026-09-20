package art.arcane.volmit.packaging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public final class NativeDevelopmentTargets {
    private static final Properties TARGETS = load();

    private NativeDevelopmentTargets() {
    }

    public static Target require(String adapter) {
        Objects.requireNonNull(adapter, "adapter");
        String bundle = TARGETS.getProperty(adapter + ".bundle");
        String java = TARGETS.getProperty(adapter + ".java");
        if (bundle == null || java == null) {
            throw new IllegalArgumentException("Unknown native development target: " + adapter);
        }
        return new Target(bundle, Integer.parseInt(java));
    }

    private static Properties load() {
        try (InputStream stream = NativeDevelopmentTargets.class.getResourceAsStream(
                "/META-INF/volmlib/native-targets.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Native development target metadata is missing");
            }
            Properties targets = new Properties();
            targets.load(stream);
            return targets;
        } catch (IOException error) {
            throw new IllegalStateException("Could not read native development target metadata", error);
        }
    }

    public record Target(String paperBundle, int javaVersion) {
    }
}
