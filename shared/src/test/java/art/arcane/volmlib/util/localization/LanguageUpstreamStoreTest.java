package art.arcane.volmlib.util.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanguageUpstreamStoreTest {
    private static final String REVISION = "30cea6163bc3a0e790d3aeb6816e79a67cac615f";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripsStateAndUpstreamCopies() throws IOException {
        LanguageDirectory directory = new LanguageDirectory(temporaryFolder.newFolder("languages").toPath());
        LanguageUpstreamStore store = new LanguageUpstreamStore(directory, manifest("main"));

        assertEquals(LanguageUpstreamStore.State.empty(), store.load());
        assertTrue(store.readUpstream("fr_FR").isEmpty());

        LanguageUpstreamStore.State state = LanguageUpstreamStore.State.empty()
                .withRevision(REVISION, Optional.of("W/\"etag\\x\""), 1234L)
                .withHash("fr_FR", LanguageUpstreamStore.sha256("content"));
        store.save(state);
        store.writeUpstream("fr_FR", "greeting = \"Bonjour\"\n");

        LanguageUpstreamStore.State loaded = store.load();
        assertEquals(Optional.of(REVISION), loaded.revision());
        assertEquals(Optional.of("W/\"etag\\x\""), loaded.etag());
        assertEquals(1234L, loaded.checkedMillis());
        assertEquals(Map.of("fr_FR", LanguageUpstreamStore.sha256("content")), loaded.hashes());
        assertEquals(Optional.of("greeting = \"Bonjour\"\n"), store.readUpstream("fr_FR"));
        assertTrue(Files.isRegularFile(directory.upstreamDirectory().resolve(LanguageUpstreamStore.STATE_FILE)));

        store.deleteUpstream("fr_FR");
        assertTrue(store.readUpstream("fr_FR").isEmpty());
        assertEquals(Map.of(), loaded.withoutHash("fr_FR").hashes());
    }

    @Test
    public void stateFromAnotherRepositoryOrBranchIsIgnored() throws IOException {
        LanguageDirectory directory = new LanguageDirectory(temporaryFolder.newFolder("languages").toPath());
        new LanguageUpstreamStore(directory, manifest("main")).save(
                LanguageUpstreamStore.State.empty().withRevision(REVISION, Optional.empty(), 5L));

        LanguageUpstreamStore.State loaded = new LanguageUpstreamStore(directory, manifest("master")).load();

        assertTrue(loaded.revision().isEmpty());
        assertEquals(0L, loaded.checkedMillis());
    }

    @Test
    public void corruptRevisionOrHashesAreDropped() throws IOException {
        LanguageDirectory directory = new LanguageDirectory(temporaryFolder.newFolder("languages").toPath());
        directory.prepareUpstream();
        Path state = directory.upstreamDirectory().resolve(LanguageUpstreamStore.STATE_FILE);
        Files.writeString(state, "repository=VolmitSoftware/Test\nbranch=main\nrevision=notasha\nchecked=x\n"
                + "sha256.fr_FR=zz\nsha256.de_DE=" + "a".repeat(64) + "\n");

        LanguageUpstreamStore.State loaded = new LanguageUpstreamStore(directory, manifest("main")).load();

        assertTrue(loaded.revision().isEmpty());
        Files.writeString(state, "repository=VolmitSoftware/Test\nbranch=main\nrevision=\nchecked=x\n"
                + "sha256.fr_FR=zz\nsha256.de_DE=" + "a".repeat(64) + "\n");
        loaded = new LanguageUpstreamStore(directory, manifest("main")).load();
        assertEquals(Map.of("de_DE", "a".repeat(64)), loaded.hashes());
        assertEquals(0L, loaded.checkedMillis());
        assertFalse(loaded.etag().isPresent());
    }

    private static LanguageSourceManifest manifest(String branch) {
        return new LanguageSourceManifest("VolmitSoftware/Test", branch, "languages", ".toml", Set.of("fr_FR", "de_DE"));
    }
}
