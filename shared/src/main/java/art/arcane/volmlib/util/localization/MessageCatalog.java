package art.arcane.volmlib.util.localization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MessageCatalog {
    private final String englishLocale;
    private final Map<String, MessageKey> keys;

    private MessageCatalog(String englishLocale, Map<String, MessageKey> keys) {
        this.englishLocale = englishLocale;
        this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    public static Builder builder(String englishLocale) {
        return new Builder(englishLocale);
    }

    public static MessageCatalog of(String englishLocale, MessageKey... keys) {
        Builder builder = builder(englishLocale);
        for (MessageKey key : keys) {
            builder.add(key);
        }
        return builder.build();
    }

    public String englishLocale() {
        return englishLocale;
    }

    public Map<String, MessageKey> byId() {
        return keys;
    }

    public List<MessageKey> keys() {
        return List.copyOf(keys.values());
    }

    public Set<String> ids() {
        return keys.keySet();
    }

    public MessageKey key(String id) {
        return keys.get(id);
    }

    public MessageKey require(String id) {
        String normalizedId = LocalizationSupport.requireMessageId(id);
        MessageKey key = keys.get(normalizedId);
        if (key == null) {
            throw new IllegalArgumentException("Unknown message key: " + normalizedId);
        }
        return key;
    }

    MessageKey requireDefinition(MessageKey definition) {
        MessageKey requested = Objects.requireNonNull(definition, "Message key cannot be null");
        MessageKey registered = require(requested.id());
        if (!registered.equals(requested)) {
            throw new IllegalArgumentException("Message key definition differs from catalog: " + requested.id());
        }
        return registered;
    }

    public static final class Builder {
        private final String englishLocale;
        private final Map<String, MessageKey> keys = new LinkedHashMap<>();
        private final List<LocalizationIssue> issues = new ArrayList<>();

        private Builder(String englishLocale) {
            this.englishLocale = LocalizationSupport.requireLocale(englishLocale);
        }

        public Builder add(MessageKey key) {
            MessageKey resolved = Objects.requireNonNull(key, "Message key cannot be null");
            MessageKey existing = keys.putIfAbsent(resolved.id(), resolved);
            if (existing == null) {
                return this;
            }

            issues.add(new LocalizationIssue(
                    LocalizationSeverity.ERROR,
                    LocalizationIssueCode.DUPLICATE_KEY,
                    "catalog",
                    resolved.id(),
                    "Message key is declared more than once"
            ));
            if (existing.shape() != resolved.shape()) {
                issues.add(new LocalizationIssue(
                        LocalizationSeverity.ERROR,
                        LocalizationIssueCode.SHAPE_MISMATCH,
                        "catalog",
                        resolved.id(),
                        "Duplicate declarations use " + existing.shape() + " and " + resolved.shape()
                ));
            }
            if (!existing.placeholders().equals(resolved.placeholders())) {
                issues.add(new LocalizationIssue(
                        LocalizationSeverity.ERROR,
                        LocalizationIssueCode.PLACEHOLDER_MISMATCH,
                        "catalog",
                        resolved.id(),
                        "Duplicate declarations use different placeholders"
                ));
            }
            return this;
        }

        public Builder addAll(Collection<? extends MessageKey> keys) {
            for (MessageKey key : keys) {
                add(key);
            }
            return this;
        }

        public MessageCatalog build() {
            LocalizationValidationResult result = new LocalizationValidationResult(issues);
            result.throwIfInvalid();
            return new MessageCatalog(englishLocale, keys);
        }
    }
}
