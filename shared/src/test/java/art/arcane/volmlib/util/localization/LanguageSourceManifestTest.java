package art.arcane.volmlib.util.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LanguageSourceManifestTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesTheBranchTrackingManifest() {
        LanguageSourceManifest manifest = LanguageSourceManifest.parse(properties(
                "repository=VolmitSoftware/ShapedPortals",
                "branch=main",
                "path=/src/main/resources/languages/",
                "extension=.toml",
                "locales=de_DE, ja-JP ,zh_TW"));

        assertEquals("VolmitSoftware", manifest.owner());
        assertEquals("ShapedPortals", manifest.name());
        assertEquals("main", manifest.branch());
        assertEquals("src/main/resources/languages", manifest.path());
        assertEquals(List.of("de_DE", "ja-JP", "zh_TW"), List.copyOf(manifest.locales()));
        assertTrue(manifest.contains("JA_JP"));
        assertFalse(manifest.contains("pirate"));
        assertEquals("ja-JP", manifest.canonical("ja_jp"));
        assertEquals("src/main/resources/languages/de_DE.toml", manifest.filePath("de_de"));
    }

    @Test
    public void defaultsPathAndExtensionAndLoadsFromAClassLoader() throws Exception {
        Path resources = temporaryFolder.newFolder().toPath();
        Files.writeString(resources.resolve("test-language-source.properties"),
                "repository=VolmitSoftware/Test\nbranch=master\nlocales=fr_FR\n");

        LanguageSourceManifest manifest = LanguageSourceManifest.load(
                new URLClassLoader(new URL[]{resources.toUri().toURL()}, null), "test-language-source.properties");

        assertEquals("src/main/resources/languages", manifest.path());
        assertEquals(".toml", manifest.extension());
        assertEquals(Set.of("fr_FR"), manifest.locales());
    }

    @Test
    public void rejectsTheRetiredPinnedFormatAndInvalidValues() {
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=main", "locales=fr_FR",
                "revision=30cea6163bc3a0e790d3aeb6816e79a67cac615f"), "retired pinned format");
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=main", "locales=fr_FR",
                "sha256.fr_FR=00"), "retired pinned format");
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=main", "locales=fr_FR",
                "mode=bundled"), "retired pinned format");
        assertRejected(properties("repository=VolmitSoftware", "branch=main", "locales=fr_FR"), "owner/repository");
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=../x", "locales=fr_FR"), "branch");
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=main", "locales="), "no locales");
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=main", "locales=fr_FR,fr_FR"), "Duplicate");
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=main", "locales=../fr"), "Invalid language");
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=main", "locales=fr_FR", "extension=toml"),
                "extension");
        assertRejected(properties("repository=VolmitSoftware/Test", "branch=main", "locales=fr_FR", "path=../etc"),
                "path");
    }

    @Test(expected = IllegalStateException.class)
    public void missingResourceFails() {
        LanguageSourceManifest.load(new URLClassLoader(new URL[0], null), "missing.properties");
    }

    private static void assertRejected(Properties properties, String fragment) {
        try {
            LanguageSourceManifest.parse(properties);
            fail("Accepted " + properties);
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().toLowerCase().contains(fragment.toLowerCase()));
        }
    }

    private static Properties properties(String... lines) {
        Properties properties = new Properties();
        for (String line : lines) {
            int separator = line.indexOf('=');
            properties.setProperty(line.substring(0, separator), line.substring(separator + 1));
        }
        return properties;
    }
}
