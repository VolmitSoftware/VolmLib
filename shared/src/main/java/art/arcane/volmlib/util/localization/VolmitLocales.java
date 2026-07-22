package art.arcane.volmlib.util.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
