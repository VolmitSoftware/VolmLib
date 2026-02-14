package art.arcane.volmlib.util.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class DUTF {
    private DUTF() {
    }

    public static void write(String s, DataOutputStream dos) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(b.length);
        dos.write(b);
    }

    public static String read(DataInputStream din) throws IOException {
        byte[] d = new byte[din.readShort()];
        din.read(d);
        return new String(d, StandardCharsets.UTF_8);
    }
}
