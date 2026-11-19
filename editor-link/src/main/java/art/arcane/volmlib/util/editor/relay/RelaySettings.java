package art.arcane.volmlib.util.editor.relay;

import java.net.URI;
import java.util.Objects;

/**
 * What the relay client needs from a product's settings. The server connects out to {@code url}'s {@code /agent} path;
 * frames over {@code frameBytes} are refused both ways, and at most {@code concurrentRequests} relayed requests run at once.
 * The product checks the URL and the ranges when it reads its config.
 */
public record RelaySettings(boolean enabled, String url, int reconnectMinSeconds, int reconnectMaxSeconds, int frameBytes, int concurrentRequests) {
    public RelaySettings {
        Objects.requireNonNull(url);
        if (reconnectMinSeconds < 1 || reconnectMaxSeconds < reconnectMinSeconds) throw new IllegalArgumentException("need 1 <= reconnectMinSeconds <= reconnectMaxSeconds");
        if (frameBytes < 1024) throw new IllegalArgumentException("frameBytes must be at least 1024");
        if (concurrentRequests < 1) throw new IllegalArgumentException("concurrentRequests must be at least 1");
    }

    /** The relay's server path: {@code url} followed by {@code /agent}. */
    public URI agentUri() {
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return URI.create(base + "/agent");
    }
}
