package art.arcane.volmlib.util.editor;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;

/**
 * What the direct listener needs from a product's settings. {@code tlsCertificate} and {@code tlsPrivateKey} are PEM files
 * relative to the data folder, both empty for plain HTTP (loopback only). {@code trustedProxies} are IP literals of reverse
 * proxies whose {@code X-Forwarded-For} is believed and which are exempt from the per-address connection limit.
 * {@code requestBodyBytes} bounds an authenticated body; requests at {@code uploadRoute} or under it may send up to
 * {@code uploadBodyBytes}, and at exactly {@code uploadRoute} the body is raw bytes that reach the handler as one base64 JSON
 * string. An empty {@code uploadRoute} has no upload.
 */
public record ListenerSettings(String listenAddress, int port, String tlsCertificate, String tlsPrivateKey, int openConnections, List<String> trustedProxies,
                               int requestBodyBytes, String uploadRoute, int uploadBodyBytes) {
    public ListenerSettings {
        Objects.requireNonNull(listenAddress);
        Objects.requireNonNull(tlsCertificate);
        Objects.requireNonNull(tlsPrivateKey);
        Objects.requireNonNull(uploadRoute);
        if (tlsCertificate.isEmpty() != tlsPrivateKey.isEmpty()) throw new IllegalArgumentException("tlsCertificate and tlsPrivateKey must be set together");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port must be within 0..65535");
        if (openConnections < 1) throw new IllegalArgumentException("openConnections must be at least 1");
        if (!uploadRoute.isEmpty() && !uploadRoute.startsWith("/v1/")) throw new IllegalArgumentException("uploadRoute must start with /v1/");
        trustedProxies = List.copyOf(trustedProxies);
    }

    public boolean tls() { return !tlsCertificate.isEmpty(); }

    /** True when {@code peer} is one of {@code trustedProxies}. */
    public boolean trustsProxy(InetAddress peer) {
        for (String proxy : trustedProxies) if (EditorAddresses.literal(proxy).filter(peer::equals).isPresent()) return true;
        return false;
    }

    /** True when {@code next} needs a new socket: the address, port, TLS files or connection bound changed. */
    public boolean rebind(ListenerSettings next) {
        return !listenAddress.equals(next.listenAddress) || port != next.port || !tlsCertificate.equals(next.tlsCertificate)
                || !tlsPrivateKey.equals(next.tlsPrivateKey) || openConnections != next.openConnections;
    }

    /** The largest body an authenticated request to {@code target} may send. */
    long bodyLimit(String target) {
        boolean upload = !uploadRoute.isEmpty() && (target.equals(uploadRoute) || target.startsWith(uploadRoute + "/") || target.startsWith(uploadRoute + "?"));
        return upload ? uploadBodyBytes : requestBodyBytes;
    }
}
