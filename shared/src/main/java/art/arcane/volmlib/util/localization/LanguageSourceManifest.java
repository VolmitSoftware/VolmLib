package art.arcane.volmlib.util.localization;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

public record LanguageSourceManifest(String repository, String branch, String path, String extension,
                                     Set<String> locales) {
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Pattern BRANCH_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}");
    private static final Pattern LOCALE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,32}");
    private static final Pattern PATH_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*");

    public LanguageSourceManifest {
        repository = requireRepository(repository);
        branch = requireBranch(branch);
        path = requirePath(path);
        extension = requireExtension(extension);
        locales = requireLocales(locales);
    }

    public static LanguageSourceManifest load(ClassLoader loader, String resource) {
        ClassLoader requiredLoader = Objects.requireNonNull(loader, "loader");
        String requiredResource = Objects.requireNonNull(resource, "resource");
        Properties properties = new Properties();
        try (InputStream input = requiredLoader.getResourceAsStream(requiredResource)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + requiredResource);
            }
            properties.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to read " + requiredResource, failure);
        }
        return parse(properties);
    }

    public static LanguageSourceManifest parse(Properties properties) {
        Properties required = Objects.requireNonNull(properties, "properties");
        for (String key : required.stringPropertyNames()) {
            if (key.equals("revision") || key.equals("mode") || key.startsWith("sha256.")) {
                throw new IllegalStateException("Language source manifest uses the retired pinned format: " + key);
            }
        }
        LinkedHashSet<String> locales = new LinkedHashSet<>();
        for (String rawLocale : required.getProperty("locales", "").split(",")) {
            String locale = rawLocale.trim();
            if (locale.isEmpty()) {
                continue;
            }
            if (!locales.add(locale)) {
                throw new IllegalStateException("Duplicate language source entry: " + locale);
            }
        }
        return new LanguageSourceManifest(
                required.getProperty("repository", ""),
                required.getProperty("branch", ""),
                required.getProperty("path", "src/main/resources/languages"),
                required.getProperty("extension", ".toml"),
                locales
        );
    }

    public String owner() {
        return repository.substring(0, repository.indexOf('/'));
    }

    public String name() {
        return repository.substring(repository.indexOf('/') + 1);
    }

    public boolean contains(String locale) {
        if (locale == null) {
            return false;
        }
        for (String candidate : locales) {
            if (LanguageDirectory.sameLocale(candidate, locale)) {
                return true;
            }
        }
        return false;
    }

    public String canonical(String locale) {
        for (String candidate : locales) {
            if (LanguageDirectory.sameLocale(candidate, locale)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported locale: " + locale);
    }

    public String filePath(String locale) {
        return path + "/" + canonical(locale) + extension;
    }

    private static String requireRepository(String repository) {
        String normalized = Objects.requireNonNull(repository, "repository").trim();
        if (!REPOSITORY_PATTERN.matcher(normalized).matches() || normalized.contains("..")) {
            throw new IllegalStateException("Language source repository must be a GitHub owner/repository");
        }
        return normalized;
    }

    private static String requireBranch(String branch) {
        String normalized = Objects.requireNonNull(branch, "branch").trim();
        if (!BRANCH_PATTERN.matcher(normalized).matches() || normalized.contains("..")
                || normalized.endsWith("/") || normalized.endsWith(".lock")) {
            throw new IllegalStateException("Language source branch is not a valid Git branch name");
        }
        return normalized;
    }

    private static String requirePath(String path) {
        String normalized = Objects.requireNonNull(path, "path").trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!PATH_PATTERN.matcher(normalized).matches() || normalized.contains("..")) {
            throw new IllegalStateException("Language source path must be a relative repository path");
        }
        return normalized;
    }

    private static String requireExtension(String extension) {
        String normalized = Objects.requireNonNull(extension, "extension").trim();
        if (!normalized.startsWith(".") || normalized.length() < 2 || !normalized.substring(1).matches("[A-Za-z0-9]+")) {
            throw new IllegalStateException("Language extension must start with .");
        }
        return normalized;
    }

    private static Set<String> requireLocales(Set<String> locales) {
        LinkedHashSet<String> required = new LinkedHashSet<>();
        for (String locale : Objects.requireNonNull(locales, "locales")) {
            String normalized = Objects.requireNonNull(locale, "locale").trim();
            if (!LOCALE_PATTERN.matcher(normalized).matches()) {
                throw new IllegalStateException("Invalid language source entry: " + locale);
            }
            if (!required.add(normalized)) {
                throw new IllegalStateException("Duplicate language source entry: " + locale);
            }
        }
        if (required.isEmpty()) {
            throw new IllegalStateException("Language source manifest contains no locales");
        }
        return Collections.unmodifiableSet(required);
    }
}
