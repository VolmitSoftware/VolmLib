package art.arcane.volmlib.util.localization;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public interface LanguagePreferenceStore {
    Map<UUID, String> load() throws IOException;

    void save(Map<UUID, String> preferences) throws IOException;

    default String description() {
        return getClass().getName();
    }
}
