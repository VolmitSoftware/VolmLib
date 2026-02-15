package art.arcane.volmlib.util.bukkit.json;

import art.arcane.volmlib.util.bukkit.registry.RegistryUtil;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

import java.io.IOException;

final class RegistryTypeAdapter<T extends Keyed> extends TypeAdapter<T> {
    private final Class<T> type;

    RegistryTypeAdapter(Class<T> type) {
        this.type = type;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        out.value(value.getKey().toString());
    }

    @Override
    public T read(JsonReader in) throws IOException {
        NamespacedKey key = NamespacedKey.fromString(in.nextString());
        if (key == null) {
            return null;
        }

        return RegistryUtil.find(type, key);
    }
}
