package art.arcane.volmlib.util.bukkit.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

import java.io.IOException;

public final class BukkitTypeAdapters {
    public static final TypeAdapter<Sound> SOUND = createRegistry(Sound.class);
    public static final TypeAdapter<Material> MATERIAL = createRegistry(Material.class);

    public static final TypeAdapter<NamespacedKey> NAMESPACED_KEY = new TypeAdapter<NamespacedKey>() {
        @Override
        public void write(JsonWriter out, NamespacedKey value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public NamespacedKey read(JsonReader in) throws IOException {
            return NamespacedKey.fromString(in.nextString());
        }
    }.nullSafe();

    public static final TypeAdapter<Vector> VECTOR = new TypeAdapter<Vector>() {
        @Override
        public void write(JsonWriter out, Vector value) throws IOException {
            out.beginArray();
            out.value(value.getX());
            out.value(value.getY());
            out.value(value.getZ());
            out.endArray();
        }

        @Override
        public Vector read(JsonReader in) throws IOException {
            in.beginArray();
            double x = in.nextDouble();
            double y = in.nextDouble();
            double z = in.nextDouble();
            in.endArray();
            return new Vector(x, y, z);
        }
    }.nullSafe();

    private BukkitTypeAdapters() {
    }

    public static <T extends Keyed> TypeAdapter<T> createRegistry(Class<T> type) {
        return new RegistryTypeAdapter<>(type).nullSafe();
    }
}
