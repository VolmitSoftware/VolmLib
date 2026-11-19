package art.arcane.volmlib.util.editor;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Replaces a file so that it is whole and on disk when the call returns (write a temporary file, force it, move it over,
 * force the directory). Each product passes its own, so its file watchers hear about the write.
 */
@FunctionalInterface
public interface DurableWrite {
    void replace(Path file, byte[] content) throws IOException;
}
