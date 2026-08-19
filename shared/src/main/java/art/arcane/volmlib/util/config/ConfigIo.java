package art.arcane.volmlib.util.config;

import java.io.File;

public interface ConfigIo {
    ConfigIo SILENT = new ConfigIo() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }
    };

    void info(String message);

    void warn(String message);

    default void verbose(String message) {
        info(message);
    }

    default File dataFolder() {
        return null;
    }
}
