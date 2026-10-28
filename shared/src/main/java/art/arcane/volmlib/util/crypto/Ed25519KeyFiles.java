package art.arcane.volmlib.util.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

public final class Ed25519KeyFiles {
    private Ed25519KeyFiles() {
    }

    public static KeyPair loadOrCreate(Path privateKeyFile, Path publicKeyFile) throws IOException {
        Path privatePath = Objects.requireNonNull(privateKeyFile, "privateKeyFile");
        Path publicPath = Objects.requireNonNull(publicKeyFile, "publicKeyFile");
        boolean privateExists = Files.isRegularFile(privatePath);
        boolean publicExists = Files.isRegularFile(publicPath);
        if (privateExists && publicExists) {
            return read(privatePath, publicPath);
        }
        if (privateExists || publicExists) {
            Path missing = privateExists ? publicPath : privatePath;
            Path present = privateExists ? privatePath : publicPath;
            throw new IOException("Ed25519 key file " + missing + " is missing. Restore it, or delete "
                    + present + " to generate a new key pair.");
        }
        return create(privatePath, publicPath);
    }

    private static KeyPair read(Path privatePath, Path publicPath) throws IOException {
        KeyPair keyPair;
        try {
            byte[] privateBytes = Base64.getDecoder().decode(Files.readString(privatePath, StandardCharsets.UTF_8).trim());
            PrivateKey privateKey = Ed25519.decodePrivateKey(privateBytes);
            PublicKey publicKey = Ed25519.decodePublicKeyText(Files.readString(publicPath, StandardCharsets.UTF_8));
            keyPair = new KeyPair(publicKey, privateKey);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IOException("Could not read the Ed25519 key pair in " + privatePath + " and " + publicPath, exception);
        }
        if (!Ed25519.matches(keyPair)) {
            throw new IOException("Ed25519 key files " + privatePath + " and " + publicPath + " do not belong to the same key pair");
        }
        return keyPair;
    }

    private static KeyPair create(Path privatePath, Path publicPath) throws IOException {
        KeyPair keyPair;
        try {
            keyPair = Ed25519.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IOException("Could not generate an Ed25519 key pair", exception);
        }
        writeAtomically(privatePath, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()) + "\n", true);
        writeAtomically(publicPath, Ed25519.encodePublicKeyText(keyPair.getPublic()) + "\n", false);
        return keyPair;
    }

    private static void writeAtomically(Path target, String content, boolean ownerOnly) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path directory = absolute.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, absolute.getFileName().toString(), ".tmp");
        try {
            if (ownerOnly) {
                restrictToOwner(temporary);
            }
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void restrictToOwner(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
        }
    }
}
