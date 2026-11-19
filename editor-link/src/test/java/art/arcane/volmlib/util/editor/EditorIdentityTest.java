package art.arcane.volmlib.util.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorIdentityTest {
    private static final String SHOWN = "state/editor/identity.json";
    private static final DurableWrite WRITE = (Path file, byte[] content) -> Files.write(file, content);

    @TempDir Path directory;

    private Path file() { return directory.resolve("identity.json"); }

    private void rewrite(String field, String value) throws IOException {
        JsonObject document = StrictJson.object(Files.readString(file()));
        document.addProperty(field, value);
        Files.writeString(file(), document.toString());
    }

    @Test
    void loadOrCreate_thenReload_sameKeyAndFingerprint() throws Exception {
        EditorIdentity created = EditorIdentity.loadOrCreate(file(), SHOWN, WRITE);
        EditorIdentity loaded = EditorIdentity.loadOrCreate(file(), SHOWN, WRITE);
        assertEquals(created.serverKey(), loaded.serverKey());
        assertEquals(created.fingerprint(), loaded.fingerprint());
        assertEquals(PairingCode.fingerprint(Base64.getUrlDecoder().decode(created.serverKey())), created.fingerprint());
        assertTrue(created.fingerprint().matches("sha256:[0-9a-f]{64}"), created.fingerprint());
        JsonObject document = StrictJson.object(Files.readString(file()));
        assertEquals(1, document.get("v").getAsInt());
        assertEquals(created.serverKey(), document.get("serverKey").getAsString());
        assertEquals(created.fingerprint(), document.get("fingerprint").getAsString());
        assertEquals(32, Base64.getUrlDecoder().decode(document.get("seed").getAsString()).length);
    }

    @Test
    void loadOrCreate_newFile_ownerOnly() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        EditorIdentity.loadOrCreate(file(), SHOWN, WRITE);
        assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(file()));
    }

    @Test
    void loadOrCreate_mismatchedServerKey_refusedNamingTheFile() throws Exception {
        EditorIdentity.loadOrCreate(file(), SHOWN, WRITE);
        rewrite("serverKey", EditorIdentity.generate().serverKey());
        String message = assertThrows(IOException.class, () -> EditorIdentity.loadOrCreate(file(), SHOWN, WRITE)).getMessage();
        assertEquals(SHOWN + " is damaged (serverKey does not match seed); delete it and restart to create a new editor identity, then pair the editor again", message);
    }

    @Test
    void loadOrCreate_badSeed_refusedNamingTheFile() throws Exception {
        EditorIdentity.loadOrCreate(file(), SHOWN, WRITE);
        rewrite("seed", "not base64url!");
        String notBase64 = assertThrows(IOException.class, () -> EditorIdentity.loadOrCreate(file(), SHOWN, WRITE)).getMessage();
        assertTrue(notBase64.startsWith(SHOWN + " is damaged (seed is not base64url)"), notBase64);
        rewrite("seed", Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
        String shortSeed = assertThrows(IOException.class, () -> EditorIdentity.loadOrCreate(file(), SHOWN, WRITE)).getMessage();
        assertTrue(shortSeed.startsWith(SHOWN + " is damaged (seed must be 32 bytes)"), shortSeed);
        Files.writeString(file(), "{\"v\": 1, \"seed\": ");
        String torn = assertThrows(IOException.class, () -> EditorIdentity.loadOrCreate(file(), SHOWN, WRITE)).getMessage();
        assertTrue(torn.startsWith(SHOWN + " is damaged ("), torn);
        assertFalse(torn.contains("\n"), torn);
    }

    @Test
    void sign_rawKey_verifies() throws Exception {
        EditorIdentity identity = EditorIdentity.generate();
        String signature = identity.sign("animus-relay/1 hello");
        assertTrue(EditorFixtures.verifies(identity.serverKey(), "animus-relay/1 hello", signature));
        assertFalse(EditorFixtures.verifies(identity.serverKey(), "animus-relay/1 hellp", signature));
        assertFalse(EditorFixtures.verifies(EditorIdentity.generate().serverKey(), "animus-relay/1 hello", signature));
    }

    @Test
    void fromSeed_sameSeed_sameKeyAndSignature() {
        byte[] seed = new byte[32];
        for (int index = 0; index < seed.length; index++) seed[index] = (byte) index;
        EditorIdentity first = EditorIdentity.fromSeed(seed), second = EditorIdentity.fromSeed(seed);
        assertEquals(first.serverKey(), second.serverKey());
        assertEquals(first.sign("x"), second.sign("x"));
        assertEquals(43, first.serverKey().length());
        assertNotEquals(first.serverKey(), EditorIdentity.generate().serverKey());
        assertThrows(IllegalArgumentException.class, () -> EditorIdentity.fromSeed(new byte[31]));
    }
}
