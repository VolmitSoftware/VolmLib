package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAccessRulesTest {
    @TempDir
    Path temporary;

    @Test
    void preparesBundledRulesAndPreservesUnchangedInputs() throws Exception {
        Path fabric = NativeAccessRules.prepare("minecraft26_2", "fabric", temporary.toFile()).toPath();
        assertTrue(Files.readString(fabric).startsWith("accessWidener v2 official\n"));
        FileTime timestamp = FileTime.fromMillis(1000L);
        Files.setLastModifiedTime(fabric, timestamp);
        assertEquals(fabric, NativeAccessRules.prepare("minecraft26_2", "fabric", temporary.toFile()).toPath());
        assertEquals(timestamp, Files.getLastModifiedTime(fabric));
        Files.writeString(fabric, "stale");
        NativeAccessRules.prepare("minecraft26_2", "fabric", temporary.toFile());
        assertTrue(Files.readString(fabric).startsWith("accessWidener v2 official\n"));
    }

    @Test
    void packagesEachLoaderAccessTransformer() throws Exception {
        for (String loader : new String[]{"forge", "neoforge"}) {
            Path rules = NativeAccessRules.prepare("minecraft26_2", loader, temporary.resolve(loader).toFile()).toPath();
            assertTrue(Files.readString(rules).contains("public net.minecraft.server.MinecraftServer levels"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> NativeAccessRules.prepare("minecraft26_2", "../unknown", temporary.toFile()));
    }
}
