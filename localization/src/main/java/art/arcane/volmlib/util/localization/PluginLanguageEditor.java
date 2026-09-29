package art.arcane.volmlib.util.localization;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginLanguageEditor implements AutoCloseable {
    private final PluginLanguageService languages;
    private final Options options;
    private final Set<CompletableFuture<Document>> pending = ConcurrentHashMap.newKeySet();
    private final Object lifecycleLock = new Object();
    private volatile boolean closed;

    public PluginLanguageEditor(PluginLanguageService languages, Options options) {
        this.languages = Objects.requireNonNull(languages, "languages");
        this.options = Objects.requireNonNull(options, "options");
    }

    public CompletableFuture<Document> load(String locale) {
        return submit(result -> {
            String canonical = availableLocale(locale);
            LocalizationSnapshot snapshot = loadSnapshot(canonical);
            requireActive(result);
            return new Document(canonical, snapshot);
        });
    }

    public CompletableFuture<Document> save(Edit edit) {
        Edit required = Objects.requireNonNull(edit, "edit");
        return submit(result -> savePrepared(required, result));
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            for (CompletableFuture<Document> result : pending) {
                result.cancel(true);
            }
        }
    }

    private CompletableFuture<Document> submit(EditorAction action) {
        synchronized (lifecycleLock) {
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("Language editor is closed"));
            }
            CompletableFuture<Document> result = languages.submitWork(future -> {
                requireActive(future);
                Document document = action.run(future);
                requireActive(future);
                return document;
            });
            pending.add(result);
            result.whenComplete((document, failure) -> pending.remove(result));
            return result;
        }
    }

    private LocalizationSnapshot loadSnapshot(String locale) throws Exception {
        return Objects.requireNonNull(options.loader().load(locale), "Loaded snapshot");
    }

    private Document savePrepared(Edit edit, CompletableFuture<Document> result) throws Exception {
        String canonical = availableLocale(edit.locale());
        Edit current = new Edit(canonical, edit.key(), edit.expected(), edit.value());
        LocalizationSnapshot latest = loadSnapshot(canonical);
        requireActive(result);
        MessageKey definition = latest.catalog().require(current.key());
        if (!latest.value(definition).equals(current.expected())) {
            throw new IOException("Language message changed while the editor was open; reopen it and try again");
        }
        LocaleOverlay replacement = LocaleOverlay.builder("editor", canonical)
                .put(current.key(), current.value()).build();
        LocalizationValidator.validate(latest.catalog(), List.of(replacement)).throwIfInvalid();
        requireActive(result);
        LocalizationSnapshot updated = Objects.requireNonNull(options.writer().write(current), "Saved snapshot");
        if (!updated.catalog().byId().equals(latest.catalog().byId())
                || !updated.value(definition).equals(current.value())) {
            throw new IOException("Language writer did not return the saved message");
        }
        languages.cache(canonical, updated);
        return new Document(canonical, updated);
    }

    private String availableLocale(String locale) {
        String requested = LocalizationSupport.requireLocale(locale).replace('-', '_');
        for (String available : languages.availableLocales()) {
            if (available.replace('-', '_').equalsIgnoreCase(requested)) {
                return available;
            }
        }
        throw new IllegalArgumentException("Language is not available: " + locale);
    }

    private void requireActive(CompletableFuture<Document> result) {
        if (closed || result.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Language edit was cancelled");
        }
    }

    public record Options(SnapshotLoader loader, MessageWriter writer) {
        public Options {
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(writer, "writer");
        }
    }

    public record Edit(String locale, String key, MessageValue expected, MessageValue value) {
        public Edit {
            locale = LocalizationSupport.requireLocale(locale);
            key = LocalizationSupport.requireMessageId(key);
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(value, "value");
        }
    }

    public record Document(String locale, LocalizationSnapshot snapshot) {
        public Document {
            locale = LocalizationSupport.requireLocale(locale);
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    @FunctionalInterface
    public interface SnapshotLoader {
        LocalizationSnapshot load(String locale) throws Exception;
    }

    @FunctionalInterface
    public interface MessageWriter {
        LocalizationSnapshot write(Edit edit) throws Exception;
    }

    @FunctionalInterface
    private interface EditorAction {
        Document run(CompletableFuture<Document> result) throws Exception;
    }
}
