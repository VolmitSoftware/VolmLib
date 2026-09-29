package art.arcane.volmlib.util.localization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LanguageReferenceRenderer {
    private LanguageReferenceRenderer() {
    }

    public static String render(MessageCatalog catalog, List<String> headerLines) {
        MessageCatalog requiredCatalog = Objects.requireNonNull(catalog, "Message catalog cannot be null");
        Map<String, MessageValue> values = new LinkedHashMap<>();
        for (MessageKey key : requiredCatalog.keys()) {
            values.put(key.id(), key.englishValue());
        }
        return TomlLanguageWriter.render(values, headerLines);
    }
}
