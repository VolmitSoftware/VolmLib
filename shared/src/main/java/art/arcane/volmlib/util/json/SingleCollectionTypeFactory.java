package art.arcane.volmlib.util.json;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collection;

public class SingleCollectionTypeFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!Collection.class.isAssignableFrom(type.getRawType())) {
            return null;
        }

        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
        TypeAdapter<JsonElement> element = gson.getAdapter(JsonElement.class);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.BEGIN_ARRAY) {
                    return delegate.read(in);
                }

                JsonArray array = new JsonArray();
                array.add(element.read(in));
                return delegate.fromJsonTree(array);
            }
        }.nullSafe();
    }
}
