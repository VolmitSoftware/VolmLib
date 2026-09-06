package art.arcane.volmit.packaging;

import com.googlecode.pngtastic.core.processing.zopfli.Options;
import com.googlecode.pngtastic.core.processing.zopfli.Zopfli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class ReleaseDeflater {
    private static final Options OPTIONS = new Options(
            Options.OutputFormat.DEFLATE, Options.BlockSplitting.FIRST, 15);

    private final Zopfli zopfli = new Zopfli(256 * 1024);

    public byte[] compress(byte[] input) throws IOException {
        byte[] standard = standardCompression(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream(standard.length);
        zopfli.compress(OPTIONS, input, output);
        byte[] optimized = output.toByteArray();
        byte[] result = optimized.length < standard.length ? optimized : standard;
        verify(input, result);
        return result;
    }

    private byte[] standardCompression(byte[] input) {
        Deflater deflater = new Deflater(9, true);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int length = deflater.deflate(buffer);
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private void verify(byte[] input, byte[] compressed) throws IOException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(compressed);
            byte[] buffer = new byte[8192];
            int offset = 0;
            while (!inflater.finished()) {
                int length = inflater.inflate(buffer);
                if (length == 0 && !inflater.finished()) {
                    throw new IOException("Release compression produced an incomplete DEFLATE stream.");
                }
                if (offset + length > input.length
                        || !Arrays.equals(input, offset, offset + length, buffer, 0, length)) {
                    throw new IOException("Release compression changed entry content.");
                }
                offset += length;
            }
            if (offset != input.length || inflater.getRemaining() != 0) {
                throw new IOException("Release compression changed entry length.");
            }
        } catch (DataFormatException failure) {
            throw new IOException("Release compression produced invalid DEFLATE data.", failure);
        } finally {
            inflater.end();
        }
    }
}
