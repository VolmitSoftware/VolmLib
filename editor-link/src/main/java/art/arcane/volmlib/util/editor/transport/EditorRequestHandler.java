package art.arcane.volmlib.util.editor.transport;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * The transport-neutral editor API under a product's {@code /v1} path. The direct listener and the relay client both call it, so every check
 * (tokens, scopes, counters, origins, limits) lives behind it. Implementations must not block the calling thread on
 * server work: complete the stage from the file lane or the server scheduler.
 */
public interface EditorRequestHandler {
    /**
     * What a request head earns before its body is read: a refusal to send instead, or whether the request carried a valid
     * token, which decides how large a body the transport accepts.
     */
    record Admission(Optional<EditorResponse> refusal, boolean authenticated) {
        public static final Admission OPEN = new Admission(Optional.empty(), false), AUTHENTICATED = new Admission(Optional.empty(), true);

        public static Admission refused(EditorResponse response) { return new Admission(Optional.of(response), false); }
    }

    /**
     * Checks the head of a request (its body empty) before the transport reads the body, on the calling thread and without
     * blocking. The default admits it unauthenticated, so the transport reads no more than a small body.
     */
    default Admission admit(EditorRequest head) { return Admission.OPEN; }

    /** Answers one request other than {@code GET /v1/events}. */
    CompletionStage<EditorResponse> handle(EditorRequest request);

    /**
     * Opens {@code GET /v1/events}. Over the relay the token is in the request's {@code Authorization} header instead of an
     * {@code event-auth} frame. A 2xx {@link EditorEventStream#response()} keeps the stream open and {@code sink} receives
     * events until either side closes it; any other status ends it.
     */
    CompletionStage<EditorEventStream> openEvents(EditorRequest request, EditorEventSink sink);
}
