package art.arcane.volmlib.util.io;

import java.io.File;

public interface Converter {
    String getInExtension();

    @SuppressWarnings("SameReturnValue")
    String getOutExtension();

    void convert(File in, File out);
}
