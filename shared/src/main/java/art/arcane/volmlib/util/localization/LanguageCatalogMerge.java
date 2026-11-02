package art.arcane.volmlib.util.localization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LanguageCatalogMerge {
    private LanguageCatalogMerge() {
    }

    public static MergeResult merge(MessageCatalog catalog, String local, String previousUpstream, String nextUpstream)
            throws IOException {
        MessageCatalog requiredCatalog = Objects.requireNonNull(catalog, "catalog");
        String requiredNext = Objects.requireNonNull(nextUpstream, "nextUpstream");
        String localSource = local == null ? "" : local;
        LinkedHashMap<String, JsonElement> localValues = TomlLanguageWriter.flattenedValues(
                TomlLanguageEditor.parse(localSource));
        LinkedHashMap<String, JsonElement> previousValues = previousUpstream == null
                ? new LinkedHashMap<>()
                : TomlLanguageWriter.flattenedValues(TomlLanguageEditor.parse(previousUpstream));
        JsonObject parsedNext = TomlLanguageEditor.parse(requiredNext);
        LinkedHashMap<String, JsonElement> rawNextValues = TomlLanguageWriter.flattenedValues(parsedNext);
        LinkedHashMap<String, JsonElement> nextValues = validUpstreamValues(requiredCatalog, parsedNext);

        ArrayList<String> added = new ArrayList<>();
        ArrayList<String> updated = new ArrayList<>();
        ArrayList<String> kept = new ArrayList<>();
        ArrayList<String> removed = new ArrayList<>();
        LinkedHashMap<String, JsonElement> merged = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : localValues.entrySet()) {
            String key = entry.getKey();
            JsonElement localValue = entry.getValue();
            JsonElement previousValue = previousValues.get(key);
            JsonElement nextValue = nextValues.get(key);
            if (nextValue != null) {
                if (nextValue.equals(localValue)) {
                    merged.put(key, localValue);
                } else if (previousValue != null && previousValue.equals(localValue)) {
                    merged.put(key, nextValue);
                    updated.add(key);
                } else {
                    merged.put(key, localValue);
                    kept.add(key);
                }
                continue;
            }
            if (previousValue != null && previousValue.equals(localValue) && !rawNextValues.containsKey(key)
                    && !isReserved(key)) {
                removed.add(key);
                continue;
            }
            merged.put(key, localValue);
        }
        for (Map.Entry<String, JsonElement> entry : nextValues.entrySet()) {
            if (!merged.containsKey(entry.getKey()) && !localValues.containsKey(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
                added.add(entry.getKey());
            }
        }

        String header = TomlLanguageEditor.leadingCommentBlock(requiredNext);
        if (header.isEmpty()) {
            header = TomlLanguageEditor.leadingCommentBlock(localSource);
        }
        JsonObject mergedValues = new JsonObject();
        merged.forEach(mergedValues::add);
        String content = header + TomlLanguageWriter.renderJson(mergedValues, List.of());
        boolean changed = !normalize(content).equals(normalize(localSource));
        return new MergeResult(content, List.copyOf(added), List.copyOf(updated), List.copyOf(kept),
                List.copyOf(removed), changed);
    }

    private static LinkedHashMap<String, JsonElement> validUpstreamValues(MessageCatalog catalog, JsonObject upstream) {
        JsonObject serialized = new JsonObject();
        for (Map.Entry<String, MessageValue> entry : TomlLanguageParser.validValues(upstream, catalog).entrySet()) {
            serialized.add(entry.getKey(), TomlLanguageWriter.serializeValue(entry.getValue()));
        }
        return TomlLanguageWriter.flattenedValues(serialized);
    }

    private static boolean isReserved(String key) {
        return key.equals(LanguageMeta.TABLE) || key.startsWith(LanguageMeta.TABLE + ".");
    }

    private static String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    public record MergeResult(String content, List<String> added, List<String> updated, List<String> kept,
                              List<String> removed, boolean changed) {
        public MergeResult {
            content = Objects.requireNonNull(content, "content");
            added = List.copyOf(added);
            updated = List.copyOf(updated);
            kept = List.copyOf(kept);
            removed = List.copyOf(removed);
        }

        public int changes() {
            return added.size() + updated.size() + removed.size();
        }

        public String summary() {
            return added.size() + " added, " + updated.size() + " updated, " + removed.size() + " removed, "
                    + kept.size() + " kept local";
        }
    }
}
