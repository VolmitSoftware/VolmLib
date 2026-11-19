package art.arcane.volmlib.util.editor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** TLS from an operator's PEM certificate chain and unencrypted PKCS#8 private key (RSA, EC or Ed25519). */
public final class PemTls {
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN PRIVATE KEY-----([A-Za-z0-9+/=\\s]+)-----END PRIVATE KEY-----");

    private PemTls() {}

    public static SSLContext context(Path certificate, Path privateKey) throws IOException {
        try {
            byte[] chainBytes = Files.readAllBytes(certificate);
            Collection<? extends Certificate> chain = CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(chainBytes));
            if (chain.isEmpty()) throw new IOException(certificate + " holds no PEM certificate");
            PrivateKey key = key(Files.readString(privateKey, StandardCharsets.US_ASCII), privateKey);
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            char[] password = new char[0];
            store.setKeyEntry("editor", key, password, chain.toArray(new Certificate[0]));
            KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(store, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            return context;
        } catch (GeneralSecurityException invalid) {
            throw new IOException("Cannot use the editor TLS certificate and key: " + invalid.getMessage(), invalid);
        }
    }

    private static PrivateKey key(String pem, Path file) throws IOException, GeneralSecurityException {
        if (pem.contains("BEGIN RSA PRIVATE KEY") || pem.contains("BEGIN EC PRIVATE KEY") || pem.contains("BEGIN ENCRYPTED PRIVATE KEY"))
            throw new IOException(file + " must be an unencrypted PKCS#8 key (BEGIN PRIVATE KEY); convert it with: openssl pkcs8 -topk8 -nocrypt -in old.key -out new.key");
        Matcher matcher = PRIVATE_KEY.matcher(pem);
        if (!matcher.find()) throw new IOException(file + " holds no PEM private key");
        byte[] der = Base64.getMimeDecoder().decode(matcher.group(1));
        GeneralSecurityException last = null;
        for (String algorithm : List.of("RSA", "EC", "Ed25519", "Ed448")) {
            try { return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der)); }
            catch (GeneralSecurityException wrong) { last = wrong; }
        }
        throw new IOException(file + " is not an RSA, EC or EdDSA key", last);
    }
}
