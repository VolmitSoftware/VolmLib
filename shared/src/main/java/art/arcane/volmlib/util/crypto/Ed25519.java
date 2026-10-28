package art.arcane.volmlib.util.crypto;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

public final class Ed25519 {
    public static final String ALGORITHM = "Ed25519";
    public static final int NONCE_LENGTH = 32;
    public static final int SIGNATURE_LENGTH = 64;
    public static final int MAX_ENCODED_KEY_LENGTH = 512;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ed25519() {
    }

    public static boolean isAvailable() {
        try {
            KeyFactory.getInstance(ALGORITHM);
            KeyPairGenerator.getInstance(ALGORITHM);
            Signature.getInstance(ALGORITHM);
            return true;
        } catch (NoSuchAlgorithmException exception) {
            return false;
        }
    }

    public static byte[] newNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
    }

    public static byte[] sign(PrivateKey privateKey, byte[] payload) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.initSign(Objects.requireNonNull(privateKey, "privateKey"));
        signature.update(Objects.requireNonNull(payload, "payload"));
        return signature.sign();
    }

    public static boolean verify(PublicKey publicKey, byte[] signatureBytes, byte[] payload) {
        if (publicKey == null || signatureBytes == null || payload == null || signatureBytes.length != SIGNATURE_LENGTH) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payload);
            return signature.verify(signatureBytes);
        } catch (InvalidKeyException | SignatureException exception) {
            return false;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Ed25519 is unavailable in this Java runtime", exception);
        }
    }

    public static boolean matches(KeyPair keyPair) {
        try {
            byte[] probe = newNonce();
            return verify(keyPair.getPublic(), sign(keyPair.getPrivate(), probe), probe);
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    public static PublicKey decodePublicKey(byte[] encoded) throws GeneralSecurityException {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_KEY_LENGTH) {
            throw new InvalidKeySpecException("Invalid Ed25519 public key length");
        }
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
    }

    public static PrivateKey decodePrivateKey(byte[] encoded) throws GeneralSecurityException {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_KEY_LENGTH) {
            throw new InvalidKeySpecException("Invalid Ed25519 private key length");
        }
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    public static String encodePublicKeyText(PublicKey publicKey) {
        return encodePublicKeyText(publicKey.getEncoded());
    }

    public static String encodePublicKeyText(byte[] encodedPublicKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encodedPublicKey);
    }

    public static PublicKey decodePublicKeyText(String text) throws GeneralSecurityException {
        if (text == null || text.isBlank()) {
            throw new InvalidKeySpecException("Ed25519 public key text is blank");
        }
        byte[] encoded;
        try {
            encoded = Base64.getUrlDecoder().decode(text.trim());
        } catch (IllegalArgumentException exception) {
            throw new InvalidKeySpecException("Ed25519 public key text is not URL-safe Base64", exception);
        }
        return decodePublicKey(encoded);
    }

    public static boolean sameKey(PublicKey expected, PublicKey actual) {
        return expected != null && actual != null && sameKey(expected.getEncoded(), actual.getEncoded());
    }

    public static boolean sameKey(byte[] expected, byte[] actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected, actual);
    }

    public static String fingerprint(PublicKey publicKey) {
        return fingerprint(publicKey.getEncoded());
    }

    public static String fingerprint(byte[] encodedPublicKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey);
            StringBuilder builder = new StringBuilder(23);
            for (int index = 0; index < 8; index++) {
                if (index > 0) {
                    builder.append(':');
                }
                builder.append(Character.forDigit((hash[index] >> 4) & 0xF, 16));
                builder.append(Character.forDigit(hash[index] & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable in this Java runtime", exception);
        }
    }
}
