package art.arcane.volmlib.util.editor.transport;

import com.google.gson.JsonObject;

/** Where an open {@code /v1/events} stream sends {@code event} messages. Safe to call from any thread. */
public interface EditorEventSink {
    /** Queues one event; false when the stream is closed or the event could not be delivered. */
    boolean send(JsonObject event);

    /** Ends the stream from the server side, for example after a {@code closing} event. */
    void close();
}
