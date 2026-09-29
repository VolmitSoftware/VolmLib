package art.arcane.volmlib.util.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Type;

public final class ConfigJson {
    public static final Gson NORMAL = create(false);
    public static final Gson PRETTY = create(true);

    private ConfigJson() {
    }

    public static String toJson(Object src, boolean pretty) {
        return toJson(src, src.getClass(), pretty);
    }

    public static String toJson(Object src, Type type, boolean pretty) {
        return (pretty ? PRETTY : NORMAL).toJson(src, type);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return NORMAL.fromJson(json, type);
    }

    public static <T> T fromJson(String json, Type type) {
        return NORMAL.fromJson(json, type);
    }

    private static Gson create(boolean pretty) {
        GsonBuilder builder = new GsonBuilder()
                .setLenient()
                .disableHtmlEscaping();
        if (pretty) {
            builder.setPrettyPrinting();
        }
        return builder.create();
    }
}
