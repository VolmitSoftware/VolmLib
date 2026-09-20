package art.arcane.volmit.packaging;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class NativeAccessRules {
    private NativeAccessRules() {
    }

    public static File prepare(String implementation, String loader, File directory) {
        if (!implementation.matches("minecraft[0-9]+(?:_[0-9]+)+")) {
            throw new IllegalArgumentException("Invalid native implementation: " + implementation);
        }
        String name = switch (loader) {
            case "fabric" -> "volmlib.accesswidener";
            case "forge", "neoforge" -> "accesstransformer.cfg";
            default -> throw new IllegalArgumentException("Unknown native loader: " + loader);
        };
        String resource = "/META-INF/volmlib/native-access/" + implementation + "/" + loader + "/" + name;
        try (InputStream stream = NativeAccessRules.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Native access rules are missing: " + loader);
            }
            byte[] rules = stream.readAllBytes();
            Path output = directory.toPath().resolve(name);
            if (!Files.isRegularFile(output) || !Arrays.equals(Files.readAllBytes(output), rules)) {
                Files.createDirectories(output.getParent());
                Files.write(output, rules);
            }
            return output.toFile();
        } catch (IOException error) {
            throw new UncheckedIOException("Could not prepare native access rules: " + loader, error);
        }
    }
}
