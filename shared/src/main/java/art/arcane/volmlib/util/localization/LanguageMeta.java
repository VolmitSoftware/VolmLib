package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record LanguageMeta(Optional<String> name, Optional<String> base) {
    public static final String TABLE = "meta";
    public static final String NAME_KEY = "name";
    public static final String BASE_KEY = "base";
    public static final int MAXIMUM_NAME_LENGTH = 64;
    private static final Pattern LOCALE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,32}");
    private static final LanguageMeta EMPTY = new LanguageMeta(Optional.empty(), Optional.empty());

    public LanguageMeta {
        name = normalizeName(Objects.requireNonNull(name, "name").orElse(null));
        base = normalizeBase(Objects.requireNonNull(base, "base").orElse(null));
    }

    public static LanguageMeta empty() {
        return EMPTY;
    }

    public static LanguageMeta of(String name, String base) {
        return new LanguageMeta(Optional.ofNullable(name), Optional.ofNullable(base));
    }

    public static LanguageMeta parse(String toml) throws IOException {
        JsonElement parsed = TomlCodec.toJsonElement(Objects.requireNonNullElse(toml, ""));
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Language source is not a TOML table");
        }
        return from(parsed.getAsJsonObject());
    }

    public static LanguageMeta from(JsonObject root) {
        JsonElement table = Objects.requireNonNull(root, "root").get(TABLE);
        if (table == null || !table.isJsonObject()) {
            return EMPTY;
        }
        JsonObject meta = table.getAsJsonObject();
        return new LanguageMeta(text(meta.get(NAME_KEY)), text(meta.get(BASE_KEY)));
    }

    public boolean isEmpty() {
        return name.isEmpty() && base.isEmpty();
    }

    public boolean hasBase() {
        return base.isPresent();
    }

    public JsonObject toJson() {
        JsonObject meta = new JsonObject();
        name.ifPresent(value -> meta.addProperty(NAME_KEY, value));
        base.ifPresent(value -> meta.addProperty(BASE_KEY, value));
        return meta;
    }

    public LanguageMeta withoutSelfBase(String locale) {
        String requiredLocale = Objects.requireNonNull(locale, "locale");
        if (base.isPresent() && sameLocale(base.get(), requiredLocale)) {
            return new LanguageMeta(name, Optional.empty());
        }
        return this;
    }

    public static boolean sameLocale(String first, String second) {
        return first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
    }

    private static Optional<String> text(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsString());
    }

    private static Optional<String> normalizeName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAXIMUM_NAME_LENGTH
                || trimmed.chars().anyMatch(Character::isISOControl)) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    private static Optional<String> normalizeBase(String base) {
        if (base == null) {
            return Optional.empty();
        }
        String trimmed = base.strip();
        if (!LOCALE_PATTERN.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }
}
