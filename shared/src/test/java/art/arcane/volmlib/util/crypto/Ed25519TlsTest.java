package art.arcane.volmlib.util.crypto;

import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class Ed25519TlsTest {
    @Test
    public void selfSignedCertificateCarriesTheIdentityKey() throws Exception {
        KeyPair identity = Ed25519.generateKeyPair();

        X509Certificate certificate = Ed25519Tls.selfSignedCertificate(identity, "node");

        certificate.verify(identity.getPublic());
        certificate.checkValidity();
        assertTrue(Ed25519.sameKey(identity.getPublic(), certificate.getPublicKey()));
        assertEquals("CN=node", certificate.getSubjectX500Principal().getName());
        assertEquals(3, certificate.getVersion());
    }

    @Test
    public void mutuallyPinnedPeersExchangeDataAndLearnEachOthersKeys() throws Exception {
        KeyPair server = Ed25519.generateKeyPair();
        KeyPair client = Ed25519.generateKeyPair();
        SSLContext serverContext = Ed25519Tls.context(server, "server", List.of(client.getPublic()));
        SSLContext clientContext = Ed25519Tls.context(client, "client", List.of(server.getPublic()));
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<PublicKey> accepted = executor.submit(() -> {
                try (Socket raw = listener.accept();
                     SSLSocket socket = Ed25519Tls.serverSocket(serverContext, raw)) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    output.writeUTF("echo:" + input.readUTF());
                    output.flush();
                    return Ed25519Tls.peerKey(socket.getSession());
                }
            });

            try (Socket raw = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                 SSLSocket socket = Ed25519Tls.clientSocket(clientContext, raw, "localhost", listener.getLocalPort())) {
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeUTF("hello");
                output.flush();
                assertEquals("echo:hello", new DataInputStream(socket.getInputStream()).readUTF());
                assertEquals(Ed25519Tls.PROTOCOL, socket.getSession().getProtocol());
                assertTrue(Ed25519.sameKey(server.getPublic(), Ed25519Tls.peerKey(socket.getSession())));
            }
            assertTrue(Ed25519.sameKey(client.getPublic(), accepted.get(5L, TimeUnit.SECONDS)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void untrustedClientAndUntrustedServerAreBothRefused() throws Exception {
        KeyPair server = Ed25519.generateKeyPair();
        KeyPair client = Ed25519.generateKeyPair();
        KeyPair stranger = Ed25519.generateKeyPair();

        assertHandshakeFails(
                Ed25519Tls.context(server, "server", List.of(stranger.getPublic())),
                Ed25519Tls.context(client, "client", List.of(server.getPublic())));
        assertHandshakeFails(
                Ed25519Tls.context(server, "server", List.of(client.getPublic())),
                Ed25519Tls.context(client, "client", List.of(stranger.getPublic())));
    }

    private static void assertHandshakeFails(SSLContext serverContext, SSLContext clientContext) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<Void> accepted = executor.submit(() -> {
                try (Socket raw = listener.accept();
                     SSLSocket socket = Ed25519Tls.serverSocket(serverContext, raw)) {
                    socket.getInputStream().read();
                }
                return null;
            });

            assertThrows(IOException.class, () -> {
                try (Socket raw = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                     SSLSocket socket = Ed25519Tls.clientSocket(clientContext, raw, "localhost", listener.getLocalPort())) {
                    socket.getOutputStream().write(1);
                    socket.getOutputStream().flush();
                    if (socket.getInputStream().read() < 0) {
                        throw new IOException("closed by peer");
                    }
                }
            });
            ExecutionException failure = assertThrows(ExecutionException.class, () -> accepted.get(5L, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException);
        } finally {
            executor.shutdownNow();
        }
    }
}
