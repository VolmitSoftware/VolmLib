package art.arcane.volmlib.util.crypto;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class Ed25519Test {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void signaturesVerifyOnlyForTheSignedPayloadAndKey() throws Exception {
        KeyPair signer = Ed25519.generateKeyPair();
        KeyPair stranger = Ed25519.generateKeyPair();
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        byte[] signature = Ed25519.sign(signer.getPrivate(), payload);

        assertEquals(Ed25519.SIGNATURE_LENGTH, signature.length);
        assertTrue(Ed25519.verify(signer.getPublic(), signature, payload));
        assertFalse(Ed25519.verify(stranger.getPublic(), signature, payload));
        assertFalse(Ed25519.verify(signer.getPublic(), signature, "tampered".getBytes(StandardCharsets.UTF_8)));
        assertFalse(Ed25519.verify(signer.getPublic(), new byte[3], payload));
    }

    @Test
    public void publicKeyTextRoundTrips() throws Exception {
        PublicKey publicKey = Ed25519.generateKeyPair().getPublic();
        PublicKey decoded = Ed25519.decodePublicKeyText(" " + Ed25519.encodePublicKeyText(publicKey) + "\n");

        assertTrue(Ed25519.sameKey(publicKey, decoded));
        assertEquals(23, Ed25519.fingerprint(publicKey).length());
        assertThrows(GeneralSecurityException.class, () -> Ed25519.decodePublicKeyText("not a key"));
        assertThrows(GeneralSecurityException.class, () -> Ed25519.decodePublicKeyText(""));
    }

    @Test
    public void encodedKeyBytesMatchThePublicKeyForms() throws Exception {
        PublicKey publicKey = Ed25519.generateKeyPair().getPublic();
        byte[] encoded = publicKey.getEncoded();
        byte[] copy = encoded.clone();
        byte[] other = Ed25519.generateKeyPair().getPublic().getEncoded();

        assertEquals(Ed25519.fingerprint(publicKey), Ed25519.fingerprint(encoded));
        assertEquals(Ed25519.encodePublicKeyText(publicKey), Ed25519.encodePublicKeyText(encoded));
        assertTrue(Ed25519.sameKey(encoded, copy));
        assertFalse(Ed25519.sameKey(encoded, other));
        assertFalse(Ed25519.sameKey(encoded, (byte[]) null));
        assertFalse(Ed25519.sameKey((byte[]) null, copy));
    }

    @Test
    public void fingerprintIsTheFirstEightSha256BytesInColonHex() throws Exception {
        byte[] encoded = Ed25519.generateKeyPair().getPublic().getEncoded();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(encoded);
        StringBuilder expected = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            if (index > 0) {
                expected.append(':');
            }
            expected.append(String.format("%02x", hash[index] & 0xFF));
        }

        assertEquals(expected.toString(), Ed25519.fingerprint(encoded));
    }

    @Test
    public void keyFilesAreGeneratedOnceAndReloaded() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("keys");
        Path privateKey = directory.resolve("node.key");
        Path publicKey = directory.resolve("node.pub");

        KeyPair created = Ed25519KeyFiles.loadOrCreate(privateKey, publicKey);
        KeyPair loaded = Ed25519KeyFiles.loadOrCreate(privateKey, publicKey);

        assertTrue(Ed25519.sameKey(created.getPublic(), loaded.getPublic()));
        assertTrue(Ed25519.matches(loaded));
        assertEquals(Ed25519.encodePublicKeyText(created.getPublic()),
                Files.readString(publicKey, StandardCharsets.UTF_8).trim());
    }

    @Test
    public void keyFilesRejectAHalfPresentOrMismatchedPair() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath();
        Path privateKey = directory.resolve("node.key");
        Path publicKey = directory.resolve("node.pub");
        Ed25519KeyFiles.loadOrCreate(privateKey, publicKey);

        Files.writeString(publicKey, Ed25519.encodePublicKeyText(Ed25519.generateKeyPair().getPublic()));
        assertThrows(IOException.class, () -> Ed25519KeyFiles.loadOrCreate(privateKey, publicKey));

        Files.delete(publicKey);
        IOException missing = assertThrows(IOException.class, () -> Ed25519KeyFiles.loadOrCreate(privateKey, publicKey));
        assertTrue(missing.getMessage().contains("node.pub"));
        assertTrue(Files.exists(privateKey));
    }
}
