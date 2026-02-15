package art.arcane.volmlib.util.bukkit.json;

import art.arcane.volmlib.util.json.SingleCollectionTypeFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

import java.io.Reader;

public final class BukkitJson {
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setLenient()
            .serializeNulls()
            .registerTypeAdapter(Vector.class, BukkitTypeAdapters.VECTOR)
            .registerTypeHierarchyAdapter(NamespacedKey.class, BukkitTypeAdapters.NAMESPACED_KEY)
            .registerTypeHierarchyAdapter(Sound.class, BukkitTypeAdapters.SOUND)
            .registerTypeAdapter(Material.class, BukkitTypeAdapters.MATERIAL)
            .registerTypeAdapterFactory(new SingleCollectionTypeFactory())
            .create();

    private BukkitJson() {
    }

    public static <T> T parse(Reader reader, Class<T> clazz) {
        return GSON.fromJson(reader, clazz);
    }
}
