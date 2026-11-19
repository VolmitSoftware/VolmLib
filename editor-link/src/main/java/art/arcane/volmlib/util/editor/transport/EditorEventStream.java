package art.arcane.volmlib.util.editor.transport;

/**
 * The result of {@link EditorRequestHandler#openEvents}: the opening response, and what to run when the browser closes the
 * stream or its connection drops.
 */
public record EditorEventStream(EditorResponse response, Runnable onClose) {
    public static EditorEventStream refused(EditorResponse response) { return new EditorEventStream(response, () -> {}); }

    public boolean open() { return response.status() >= 200 && response.status() < 300; }
}
