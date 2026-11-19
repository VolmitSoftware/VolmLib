package art.arcane.volmlib.util.editor;

import java.util.Objects;
import java.util.function.Consumer;

/** Where the link's console lines go: {@code debug} for routine reconnects, {@code info} and {@code warn} for the operator. */
public record EditorLog(Consumer<String> debug, Consumer<String> info, Consumer<String> warn) {
    public EditorLog {
        Objects.requireNonNull(debug);
        Objects.requireNonNull(info);
        Objects.requireNonNull(warn);
    }

    /** A log without debug lines. */
    public static EditorLog of(Consumer<String> info, Consumer<String> warn) { return new EditorLog((String line) -> {}, info, warn); }
}
