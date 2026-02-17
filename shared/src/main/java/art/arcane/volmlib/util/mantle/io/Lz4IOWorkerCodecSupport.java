package art.arcane.volmlib.util.mantle.io;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Lz4IOWorkerCodecSupport implements IOWorkerCodecSupport {
    @Override
    public InputStream decode(InputStream input) throws IOException {
        return new LZ4BlockInputStream(input);
    }

    @Override
    public OutputStream encode(OutputStream output) throws IOException {
        return new LZ4BlockOutputStream(output);
    }
}
