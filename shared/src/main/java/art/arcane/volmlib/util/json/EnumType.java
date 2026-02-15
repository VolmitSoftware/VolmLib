package art.arcane.volmlib.util.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class EnumType<W extends EnumType.Object<W>, E extends Enum<E> & EnumType.Values<W>> implements TypeAdapterFactory {
    private final Class<W> type;
    private final Class<E> enumClass;

    public EnumType(Class<W> type, Class<E> enumClass) {
        this.type = type;
        this.enumClass = enumClass;
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!this.type.isAssignableFrom(type.getRawType())) {
            return null;
        }

        return (TypeAdapter<T>) createAdapter(gson, enumClass);
    }

    private <V extends Object<V>> TypeAdapter<V> createAdapter(Gson gson, Class<? extends Values<V>> enumClass) {
        Map<String, TypeAdapter<?>> typeMap = Arrays.stream(enumClass.getEnumConstants())
                .filter(v -> v.getType() != null)
                .collect(Collectors.toUnmodifiableMap(
                        Values::getSerializedName,
                        value -> gson.getDelegateAdapter(this, TypeToken.get(value.getType()))
                ));
        TypeAdapter<JsonElement> element = gson.getAdapter(JsonElement.class);

        return new TypeAdapter<V>() {
            @Override
            public void write(JsonWriter out, V value) throws IOException {
                Values<V> valueType = value.getType();
                if (valueType == null) {
                    throw new JsonParseException("Missing type");
                }

                String name = valueType.getSerializedName();
                TypeAdapter<V> delegate = (TypeAdapter<V>) typeMap.get(name);
                if (delegate == null) {
                    throw new JsonParseException("Unknown type: " + name);
                }

                JsonElement result = delegate.toJsonTree(value);
                if (result.isJsonNull()) {
                    out.nullValue();
                    return;
                }

                JsonObject object = result.getAsJsonObject();
                object.addProperty("type", name);
                element.write(out, object);
            }

            @Override
            public V read(JsonReader in) throws IOException {
                JsonObject obj = element.read(in).getAsJsonObject();
                JsonElement rawType = obj.remove("type");
                if (rawType == null) {
                    throw new JsonParseException("Missing type");
                }
                if (!rawType.isJsonPrimitive()) {
                    throw new JsonParseException("Type must be a string");
                }

                TypeAdapter<? extends V> delegate = (TypeAdapter<? extends V>) typeMap.get(rawType.getAsString());
                if (delegate == null) {
                    throw new JsonParseException("Unknown type: " + rawType.getAsString());
                }

                return delegate.fromJsonTree(obj);
            }
        }.nullSafe();
    }

    public interface Values<W> {
        String getSerializedName();

        Class<? extends W> getType();
    }

    public interface Object<W extends Object<W>> {
        Values<W> getType();
    }
}
