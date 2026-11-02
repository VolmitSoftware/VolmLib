package art.arcane.volmlib.util.localization;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LanguageReferenceRenderer {
    private LanguageReferenceRenderer() {
    }

    public static String render(MessageCatalog catalog, List<String> headerLines) {
        return render(catalog, headerLines, LanguageMeta.empty());
    }

    public static String render(MessageCatalog catalog, List<String> headerLines, LanguageMeta meta) {
        MessageCatalog requiredCatalog = Objects.requireNonNull(catalog, "Message catalog cannot be null");
        LanguageMeta requiredMeta = Objects.requireNonNull(meta, "meta");
        Map<String, MessageValue> values = new LinkedHashMap<>();
        for (MessageKey key : requiredCatalog.keys()) {
            values.put(key.id(), key.englishValue());
        }
        if (requiredMeta.isEmpty()) {
            return TomlLanguageWriter.render(values, headerLines);
        }
        JsonObject root = new JsonObject();
        root.add(LanguageMeta.TABLE, requiredMeta.toJson());
        for (Map.Entry<String, MessageValue> entry : values.entrySet()) {
            root.add(entry.getKey(), TomlLanguageWriter.serializeValue(entry.getValue()));
        }
        return TomlLanguageWriter.renderJson(root, headerLines);
    }
}
