package art.arcane.volmlib.util.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LanguageDirectoryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void scanListsOnlyTopLevelLocaleFilesSortedCaseInsensitively() throws IOException {
        Path root = temporaryFolder.newFolder("languages").toPath();
        LanguageDirectory directory = new LanguageDirectory(root);
        Files.writeString(root.resolve("pirate.toml"), "");
        Files.writeString(root.resolve("de_DE.toml"), "");
        Files.writeString(root.resolve("Zulu.toml"), "");
        Files.writeString(root.resolve("notes.txt"), "");
        Files.writeString(root.resolve("bad.locale.toml"), "");
        Files.writeString(root.resolve("x.toml"), "");
        Files.createDirectories(root.resolve(LanguageDirectory.UPSTREAM_DIRECTORY));
        Files.writeString(root.resolve(LanguageDirectory.UPSTREAM_DIRECTORY).resolve("fr_FR.toml"), "");
        Files.createDirectories(root.resolve("folder.toml"));

        assertEquals(List.of("de_DE", "pirate", "Zulu"), directory.scan());
        assertTrue(directory.isLanguageFile(root.resolve("pirate.toml").toFile()));
        assertFalse(directory.isLanguageFile(root.resolve(LanguageDirectory.UPSTREAM_DIRECTORY).resolve("fr_FR.toml")));
        assertFalse(directory.isLanguageFile(root.resolve("bad.locale.toml")));
        assertEquals(root.resolve(LanguageDirectory.UPSTREAM_DIRECTORY), directory.upstreamDirectory());
        assertEquals(root.resolve(LanguageDirectory.UPSTREAM_DIRECTORY).resolve("fr_FR.toml"), directory.upstreamFile("fr_FR"));
    }

    @Test
    public void missingRootScansEmptyAndPrepareCreatesIt() throws IOException {
        Path root = temporaryFolder.getRoot().toPath().resolve("missing").resolve("languages");
        LanguageDirectory directory = new LanguageDirectory(root);

        assertEquals(List.of(), directory.scan());
        directory.prepareUpstream();

        assertTrue(Files.isDirectory(root));
        assertTrue(Files.isDirectory(directory.upstreamDirectory()));
    }

    @Test
    public void rejectsUnsafeLocaleNamesAndFilePaths() throws IOException {
        Path root = temporaryFolder.newFolder("languages").toPath();
        LanguageDirectory directory = new LanguageDirectory(root);
        for (String unsafe : List.of("../en_US", "en US", "", "a", "x".repeat(33))) {
            try {
                directory.languageFile(unsafe);
                fail("Accepted " + unsafe);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("safe name"));
            }
        }
        Files.writeString(root.resolve("file.toml"), "");
        try {
            LanguageDirectory.prepareRegularDirectory(root.resolve("file.toml"));
            fail("Accepted a file as a directory");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not a regular directory"));
        }
    }

    @Test
    public void readsMetaAndBoundedContent() throws IOException {
        Path root = temporaryFolder.newFolder("languages").toPath();
        LanguageDirectory directory = new LanguageDirectory(root);
        Files.writeString(root.resolve("pirate.toml"), "greeting = \"Ahoy\"\n[meta]\nname = \"Pirate\"\nbase = \"pirate\"\n");

        LanguageMeta meta = directory.meta("pirate");

        assertEquals(Optional.of("Pirate"), meta.name());
        assertTrue(meta.base().isEmpty());
        assertTrue(directory.read("pirate").startsWith("greeting"));
        assertTrue(directory.meta("missing").isEmpty());
        assertTrue(directory.exists("pirate"));
        assertFalse(directory.exists("missing"));
    }

    @Test
    public void rejectsOversizedAndInvalidUtf8Files() throws IOException {
        Path root = temporaryFolder.newFolder("languages").toPath();
        LanguageDirectory directory = new LanguageDirectory(root);
        Files.write(root.resolve("bad.toml"), new byte[]{(byte) 0xC3, (byte) 0x28});
        try {
            directory.read("bad");
            fail("Accepted invalid UTF-8");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("UTF-8"));
        }
        byte[] large = new byte[(int) LanguageDirectory.MAXIMUM_BYTES + 1];
        java.util.Arrays.fill(large, (byte) ' ');
        Files.write(root.resolve("large.toml"), large);
        try {
            directory.read("large");
            fail("Accepted an oversized file");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("3 MiB"));
        }
        assertEquals("ok", LanguageDirectory.decode("ok".getBytes(StandardCharsets.UTF_8)));
    }
}
