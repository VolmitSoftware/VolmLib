package art.arcane.volmlib.util.crypto;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class Ed25519Tls {
    public static final String PROTOCOL = "TLSv1.3";

    private static final String ALIAS = "identity";
    private static final byte[] ED25519_ALGORITHM = {0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70};
    private static final byte[] COMMON_NAME_OID = {0x06, 0x03, 0x55, 0x04, 0x03};
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'");
    private static final String NO_EXPIRY = "99991231235959Z";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ed25519Tls() {
    }

    public static SSLContext context(KeyPair identity, String commonName, Collection<PublicKey> trustedPeers)
            throws GeneralSecurityException {
        Objects.requireNonNull(identity, "identity");
        List<PublicKey> peers = List.copyOf(Objects.requireNonNull(trustedPeers, "trustedPeers"));
        char[] password = new char[0];
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try {
            keyStore.load(null, password);
        } catch (IOException exception) {
            throw new GeneralSecurityException("Could not create an in-memory key store", exception);
        }
        keyStore.setKeyEntry(ALIAS, identity.getPrivate(), password,
                new Certificate[]{selfSignedCertificate(identity, commonName)});
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);

        SSLContext context = SSLContext.getInstance(PROTOCOL);
        context.init(keyManagers.getKeyManagers(), new TrustManager[]{new PinnedTrustManager(peers)}, RANDOM);
        return context;
    }

    public static SSLSocket clientSocket(SSLContext context, Socket connected, String host, int port) throws IOException {
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(connected, host, port, true);
        socket.setUseClientMode(true);
        socket.setEnabledProtocols(new String[]{PROTOCOL});
        socket.startHandshake();
        return socket;
    }

    public static SSLSocket serverSocket(SSLContext context, Socket accepted) throws IOException {
        String host = accepted.getInetAddress() == null ? null : accepted.getInetAddress().getHostAddress();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(accepted, host, accepted.getPort(), true);
        socket.setUseClientMode(false);
        socket.setNeedClientAuth(true);
        socket.setEnabledProtocols(new String[]{PROTOCOL});
        socket.startHandshake();
        return socket;
    }

    public static PublicKey peerKey(SSLSession session) throws SSLPeerUnverifiedException {
        Certificate[] chain = session.getPeerCertificates();
        if (chain.length == 0) {
            throw new SSLPeerUnverifiedException("Peer presented no certificate");
        }
        return chain[0].getPublicKey();
    }

    static X509Certificate selfSignedCertificate(KeyPair identity, String commonName) throws GeneralSecurityException {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1L);
        byte[] serial = new byte[16];
        RANDOM.nextBytes(serial);
        serial[0] &= 0x7F;
        serial[0] |= 0x01;
        byte[] name = name(commonName);
        byte[] tbs = sequence(
                tlv(0xA0, tlv(0x02, new byte[]{0x02})),
                tlv(0x02, serial),
                ED25519_ALGORITHM,
                name,
                sequence(
                        tlv(0x17, UTC_TIME.format(now).getBytes(StandardCharsets.US_ASCII)),
                        tlv(0x18, NO_EXPIRY.getBytes(StandardCharsets.US_ASCII))),
                name,
                identity.getPublic().getEncoded());
        byte[] signature = Ed25519.sign(identity.getPrivate(), tbs);
        byte[] signatureBits = new byte[signature.length + 1];
        System.arraycopy(signature, 0, signatureBits, 1, signature.length);
        byte[] encoded = sequence(tbs, ED25519_ALGORITHM, tlv(0x03, signatureBits));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] name(String commonName) {
        String value = commonName == null || commonName.isBlank() ? "VolmLib" : commonName;
        return sequence(tlv(0x31, sequence(COMMON_NAME_OID, tlv(0x0C, value.getBytes(StandardCharsets.UTF_8)))));
    }

    private static byte[] sequence(byte[]... parts) {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            content.writeBytes(part);
        }
        return tlv(0x30, content.toByteArray());
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(value.length + 6);
        output.write(tag);
        int length = value.length;
        if (length < 0x80) {
            output.write(length);
        } else {
            int octets = length > 0xFFFFFF ? 4 : length > 0xFFFF ? 3 : length > 0xFF ? 2 : 1;
            output.write(0x80 | octets);
            for (int shift = (octets - 1) * 8; shift >= 0; shift -= 8) {
                output.write((length >>> shift) & 0xFF);
            }
        }
        output.writeBytes(value);
        return output.toByteArray();
    }

    private static final class PinnedTrustManager extends X509ExtendedTrustManager {
        private final List<PublicKey> trustedPeers;

        private PinnedTrustManager(List<PublicKey> trustedPeers) {
            this.trustedPeers = trustedPeers;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            requireTrusted(chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            requireTrusted(chain);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            requireTrusted(chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            requireTrusted(chain);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            requireTrusted(chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            requireTrusted(chain);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        private void requireTrusted(X509Certificate[] chain) throws CertificateException {
            if (chain == null || chain.length == 0) {
                throw new CertificateException("Peer presented no certificate");
            }
            PublicKey presented = chain[0].getPublicKey();
            for (PublicKey trusted : trustedPeers) {
                if (Ed25519.sameKey(trusted, presented)) {
                    return;
                }
            }
            throw new CertificateException("Peer key " + Ed25519.fingerprint(presented) + " is not trusted");
        }
    }
}
