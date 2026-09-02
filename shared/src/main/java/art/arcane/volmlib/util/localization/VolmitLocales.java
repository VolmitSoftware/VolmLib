package art.arcane.volmlib.util.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class VolmitLocales {
    public static final String ENGLISH = "en_US";

    private static final List<String> NON_ENGLISH = List.of(
            "de_DE",
            "es_ES",
            "fi_FI",
            "fr_FR",
            "he_IL",
            "it_IT",
            "ja-JP",
            "ko_KR",
            "lt_LT",
            "nl_NL",
            "pl_PL",
            "pt_PT",
            "ru_RU",
            "tr_TR",
            "vi_VI",
            "zh_CN",
            "zh_TW"
    );
    private static final List<String> ALL = createAll();
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("en_US", "English (United States)"),
            Map.entry("de_DE", "German (Germany)"),
            Map.entry("es_ES", "Spanish (Spain)"),
            Map.entry("fi_FI", "Finnish (Finland)"),
            Map.entry("fr_FR", "French (France)"),
            Map.entry("he_IL", "Hebrew (Israel)"),
            Map.entry("it_IT", "Italian (Italy)"),
            Map.entry("ja-JP", "Japanese (Japan)"),
            Map.entry("ko_KR", "Korean (South Korea)"),
            Map.entry("lt_LT", "Lithuanian (Lithuania)"),
            Map.entry("nl_NL", "Dutch (Netherlands)"),
            Map.entry("pl_PL", "Polish (Poland)"),
            Map.entry("pt_PT", "Portuguese (Portugal)"),
            Map.entry("ru_RU", "Russian (Russia)"),
            Map.entry("tr_TR", "Turkish (Türkiye)"),
            Map.entry("vi_VI", "Vietnamese (Vietnam)"),
            Map.entry("zh_CN", "Simplified Chinese (China)"),
            Map.entry("zh_TW", "Traditional Chinese (Taiwan)")
    );

    private VolmitLocales() {
    }

    public static List<String> all() {
        return ALL;
    }

    public static List<String> nonEnglish() {
        return NON_ENGLISH;
    }

    public static boolean isBundled(String locale) {
        return locale != null && ALL.contains(locale.trim());
    }

    public static Optional<String> displayName(String locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DISPLAY_NAMES.get(locale.trim()));
    }

    public static String minecraftCode(String locale) {
        if (!isBundled(locale)) {
            throw new IllegalArgumentException("Unsupported bundled locale: " + locale);
        }
        return locale.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private static List<String> createAll() {
        List<String> locales = new ArrayList<>(NON_ENGLISH.size() + 1);
        locales.add(ENGLISH);
        locales.addAll(NON_ENGLISH);
        return List.copyOf(locales);
    }
}
