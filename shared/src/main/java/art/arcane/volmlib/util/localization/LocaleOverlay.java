package art.arcane.volmlib.util.localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LocaleOverlay {
    private final String source;
    private final String locale;
    private final Map<String, MessageValue> values;

    private LocaleOverlay(String source, String locale, Map<String, MessageValue> values) {
        this.source = source;
        this.locale = locale;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Builder builder(String locale) {
        return new Builder(locale, locale);
    }

    public static Builder builder(String source, String locale) {
        return new Builder(source, locale);
    }

    public String source() {
        return source;
    }

    public String locale() {
        return locale;
    }

    public Map<String, MessageValue> values() {
        return values;
    }

    public MessageValue value(String key) {
        return values.get(key);
    }

    public static final class Builder {
        private final String source;
        private final String locale;
        private final Map<String, MessageValue> values = new LinkedHashMap<>();
        private final List<LocalizationIssue> issues = new ArrayList<>();

        private Builder(String source, String locale) {
            this.source = LocalizationSupport.requireSource(source);
            this.locale = LocalizationSupport.requireLocale(locale);
        }

        public Builder text(String key, String template) {
            return put(key, new TextValue(template));
        }

        public Builder lines(String key, List<String> lines) {
            return put(key, new LinesValue(lines));
        }

        public Builder lines(String key, String... lines) {
            return lines(key, List.of(lines));
        }

        public Builder plural(String key, Map<String, String> forms) {
            return put(key, new PluralValue(forms));
        }

        public Builder put(String key, MessageValue value) {
            String id = LocalizationSupport.requireMessageId(key);
            MessageValue resolved = Objects.requireNonNull(value, "Message value cannot be null");
            if (values.putIfAbsent(id, resolved) != null) {
                issues.add(new LocalizationIssue(
                        LocalizationSeverity.ERROR,
                        LocalizationIssueCode.DUPLICATE_KEY,
                        source,
                        id,
                        "Locale overlay declares the key more than once"
                ));
            }
            return this;
        }

        public LocaleOverlay build() {
            LocalizationValidationResult result = new LocalizationValidationResult(issues);
            result.throwIfInvalid();
            return new LocaleOverlay(source, locale, values);
        }
    }
}
