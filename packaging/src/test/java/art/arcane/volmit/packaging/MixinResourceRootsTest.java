package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinResourceRootsTest {
    @TempDir
    Path temporary;

    @Test
    void rootsEveryMixinSideAndFullyQualifiedPluginOnly() throws IOException {
        assertEquals(Set.of("library/mixin/Common", "library/mixin/client/Screen",
                "library/mixin/Server", "library/Selector"), roots(Map.of("library.mixins.json", """
                {"package":"library.mixin", "mixins":["Common"], "client":["client.Screen"],
                 "server":["Server"], "plugin":"library.Selector", "refmap":"library.Unused",
                 "injectors":{"unrelated":"library.AlsoUnused"}}
                """)));
    }

    @Test
    void acceptsFullyQualifiedMixinsWithoutPackage() throws IOException {
        assertEquals(Set.of("library/Standalone"), roots(Map.of("mixins.library.json",
                "{\"mixins\":[\"library.Standalone\"]}")));
    }

    @Test
    void ignoresUnconfiguredJsonWithClassLookingStrings() throws IOException {
        assertEquals(Set.of(), roots(Map.of("settings.json",
                "{\"package\":\"library\",\"mixins\":[\"Unused\"],\"plugin\":\"library.Other\"}")));
    }

    @Test
    void readsNamesDeclaredInManifestAndFabricMetadata() throws IOException {
        assertEquals(Set.of("library/Manifest", "library/Fabric", "library/Client"), roots(Map.of(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMixinConfigs: native.json\r\n\r\n",
                "native.json", "{\"mixins\":[\"library.Manifest\"]}",
                "fabric.mod.json", "{\"mixins\":[\"common.json\",{\"config\":\"screen.json\",\"environment\":\"client\"}]}",
                "common.json", "{\"mixins\":[\"library.Fabric\"]}",
                "screen.json", "{\"client\":[\"library.Client\"]}")));
    }

    @Test
    void reportsMalformedDeclaredConfiguration() {
        IOException failure = assertThrows(IOException.class, () -> roots(Map.of(
                "library.mixins.json", "{\"mixins\":{\"wrong\":true}}")));
        assertTrue(failure.getMessage().contains("library.mixins.json"));
    }

    @Test
    void reportsMissingExplicitConfiguration() {
        IOException failure = assertThrows(IOException.class, () -> roots(Map.of(
                "fabric.mod.json", "{\"mixins\":[\"missing.json\"]}")));
        assertTrue(failure.getMessage().contains("missing.json"));
    }

    private Set<String> roots(Map<String, String> resources) throws IOException {
        Path artifact = temporary.resolve("plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(artifact))) {
            for (Map.Entry<String, String> resource : resources.entrySet()) {
                output.putNextEntry(new JarEntry(resource.getKey()));
                output.write(resource.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        try (JarFile jar = new JarFile(artifact.toFile())) {
            return MixinResourceRoots.read(jar);
        }
    }
}
