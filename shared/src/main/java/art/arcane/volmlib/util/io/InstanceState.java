package art.arcane.volmlib.util.io;

import art.arcane.volmlib.util.math.RNG;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class InstanceState {
    public static int getInstanceId() {
        try {
            return Integer.parseInt(Files.readString(instanceFile().toPath(), StandardCharsets.UTF_8).trim());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return -1;
    }

    public static void updateInstanceId() {
        try {
            File f = instanceFile();
            Files.writeString(f.toPath(), Integer.toString(RNG.r.imax()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static File instanceFile() {
        File f = new File("plugins/Iris/cache/instance");
        f.getParentFile().mkdirs();
        return f;
    }
}
