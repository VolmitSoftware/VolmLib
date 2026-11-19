package art.arcane.volmlib.util.editor;

import java.util.Locale;
import java.util.Objects;

/**
 * The names one product's editor link uses on the wire. {@code name} gives the API base ({@code /animus}), the header
 * prefix ({@code X-Animus-}) and the relay signature prefixes ({@code animus-relay/1 }, {@code animus-editor-proof/1 }).
 * {@code tokenPrefix} starts every bearer token ({@code anm_}); {@code pairingPrefix} starts every pairing code
 * ({@code ANM1.}). {@code pairCommand} is what refusals tell the user to run for a new link. {@code pairingCarriesGrant}:
 * pairing codes also list the granted scopes and folders.
 */
public record EditorProduct(String name, String tokenPrefix, String pairingPrefix, String pairCommand, boolean pairingCarriesGrant) {
    public EditorProduct {
        if (!Objects.requireNonNull(name).matches("[A-Z][A-Za-z0-9]{1,31}")) throw new IllegalArgumentException("name: a capitalised word, such as Animus");
        if (!Objects.requireNonNull(tokenPrefix).matches("[a-z]{2,8}_")) throw new IllegalArgumentException("tokenPrefix: lower-case letters and _, such as anm_");
        if (!Objects.requireNonNull(pairingPrefix).matches("[A-Z]{2,8}[0-9]\\.")) throw new IllegalArgumentException("pairingPrefix: such as ANM1.");
        Objects.requireNonNull(pairCommand);
    }

    /** The lower-case name, as in paths and signature prefixes. */
    public String id() { return name.toLowerCase(Locale.ROOT); }

    /** Where the API is served on the direct listener: {@code /animus}. */
    public String basePath() { return "/" + id(); }

    /** {@code X-Animus-<suffix>}. */
    public String header(String suffix) { return "X-" + name + "-" + suffix; }

    /** The browser's address, written by the direct listener; a copy sent by a client is dropped first. */
    public String peerHeader() { return header("Peer"); }

    /** The per-token request counter a mutating request carries. */
    public String counterHeader() { return header("Counter"); }

    /** What the server signs, followed by the relay's nonce, to register with the relay. */
    public String relayChallengePrefix() { return id() + "-relay/1 "; }

    /** What the server signs, followed by a browser's challenge, to prove the relay reached the paired key. */
    public String proofPrefix() { return id() + "-editor-proof/1 "; }

    /** A thread name such as {@code Animus-editor-listener}. */
    public String thread(String role) { return name + "-editor-" + role; }
}
