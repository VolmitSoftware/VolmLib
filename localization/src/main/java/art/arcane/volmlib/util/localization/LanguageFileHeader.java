package art.arcane.volmlib.util.localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class LanguageFileHeader {
    private LanguageFileHeader() {
    }

    public static List<String> render(Options options) {
        Options required = Objects.requireNonNull(options, "options");
        String[] text = localized(required.locale());
        ArrayList<String> lines = new ArrayList<>();
        lines.add(required.plugin() + " — " + required.locale());
        section(lines, text[0]);
        lines.add("plugins/" + required.plugin() + "/languages/" + required.locale() + ".toml");
        lines.add(text[4]);
        lines.add("/" + required.plugin().toLowerCase(Locale.ROOT) + " language server edit " + required.locale());
        section(lines, text[1]);
        lines.addAll(required.prefixLines());
        section(lines, text[2]);
        lines.addAll(required.formattingLines());
        section(lines, text[3]);
        lines.add(text[5]);
        for (Map.Entry<String, String> entry : required.variableDefinitions().entrySet()) {
            lines.add("  {" + entry.getKey() + "}  " + entry.getValue());
        }
        return List.copyOf(lines);
    }

    private static void section(List<String> lines, String label) {
        lines.add("");
        lines.add("=== " + label + " ===");
    }

    private static String[] localized(String locale) {
        return switch (locale.replace('-', '_').toLowerCase(Locale.ROOT)) {
            case "de_de" -> new String[]{"Datei bearbeiten", "Präfix", "Formatierung", "Variablen", "Diese Datei kann direkt bearbeitet werden. Lokale Änderungen bleiben erhalten; fehlende oder ungültige Einträge verwenden das eingebaute Englisch.", "Variablennamen unverändert lassen und nur die Variablen der jeweiligen Nachricht verwenden."};
            case "es_es" -> new String[]{"Editar el archivo", "Prefijo", "Formato", "Variables", "Puede editar este archivo directamente. Los cambios locales se conservan; las entradas ausentes o no válidas usan el inglés incorporado.", "Mantenga los nombres de las variables y use solo las que ya contiene cada mensaje."};
            case "fi_fi" -> new String[]{"Tiedoston muokkaus", "Etuliite", "Muotoilu", "Muuttujat", "Voit muokata tätä tiedostoa suoraan. Paikalliset muutokset säilyvät; puuttuvat tai virheelliset kohdat käyttävät sisäänrakennettua englantia.", "Säilytä muuttujien nimet ja käytä vain kunkin viestin omia muuttujia."};
            case "fr_fr" -> new String[]{"Modifier le fichier", "Préfixe", "Mise en forme", "Variables", "Ce fichier peut être modifié directement. Les modifications locales sont conservées ; les entrées manquantes ou invalides utilisent l’anglais intégré.", "Conservez les noms des variables et utilisez uniquement celles du message concerné."};
            case "he_il" -> new String[]{"עריכת הקובץ", "קידומת", "עיצוב", "משתנים", "אפשר לערוך קובץ זה ישירות. שינויים מקומיים נשמרים; ערכים חסרים או לא תקינים משתמשים באנגלית המובנית.", "יש לשמור על שמות המשתנים ולהשתמש רק במשתנים הקיימים בכל הודעה."};
            case "it_it" -> new String[]{"Modifica del file", "Prefisso", "Formattazione", "Variabili", "Puoi modificare direttamente questo file. Le modifiche locali vengono conservate; le voci mancanti o non valide usano l’inglese incorporato.", "Mantieni i nomi delle variabili e usa solo quelle previste dal singolo messaggio."};
            case "ja_jp" -> new String[]{"ファイルの編集", "接頭辞", "書式", "変数", "このファイルを直接編集できます。ローカルの変更は保持され、不足または無効な項目には内蔵の英語を使用します。", "変数名を変更せず、各メッセージに元からある変数だけを使用してください。"};
            case "ko_kr" -> new String[]{"파일 편집", "접두사", "서식", "변수", "이 파일을 직접 편집할 수 있습니다. 로컬 변경은 유지되며, 누락되거나 잘못된 항목에는 내장 영어를 사용합니다.", "변수 이름을 변경하지 말고 각 메시지에 원래 있는 변수만 사용하세요."};
            case "lt_lt" -> new String[]{"Failo redagavimas", "Priešdėlis", "Formatavimas", "Kintamieji", "Šį failą galima redaguoti tiesiogiai. Vietiniai pakeitimai išsaugomi; trūkstamiems ar netinkamiems įrašams naudojama integruota anglų kalba.", "Nekeiskite kintamųjų pavadinimų ir naudokite tik konkrečiame pranešime esančius kintamuosius."};
            case "nl_nl" -> new String[]{"Bestand bewerken", "Voorvoegsel", "Opmaak", "Variabelen", "Je kunt dit bestand rechtstreeks bewerken. Lokale wijzigingen blijven behouden; ontbrekende of ongeldige vermeldingen gebruiken het ingebouwde Engels.", "Behoud de namen van variabelen en gebruik alleen de variabelen die bij het bericht horen."};
            case "pl_pl" -> new String[]{"Edycja pliku", "Prefiks", "Formatowanie", "Zmienne", "Ten plik można edytować bezpośrednio. Lokalne zmiany są zachowywane; brakujące lub nieprawidłowe wpisy korzystają z wbudowanego angielskiego tekstu.", "Zachowaj nazwy zmiennych i używaj tylko tych, które występują w danej wiadomości."};
            case "pt_pt" -> new String[]{"Editar o ficheiro", "Prefixo", "Formatação", "Variáveis", "Pode editar este ficheiro diretamente. As alterações locais são preservadas; as entradas em falta ou inválidas usam o inglês incorporado.", "Mantenha os nomes das variáveis e use apenas as que pertencem a cada mensagem."};
            case "ru_ru" -> new String[]{"Редактирование файла", "Префикс", "Форматирование", "Переменные", "Этот файл можно редактировать напрямую. Локальные изменения сохраняются; для отсутствующих или неверных записей используется встроенный английский текст.", "Не меняйте имена переменных и используйте только переменные исходного сообщения."};
            case "tr_tr" -> new String[]{"Dosyayı düzenleme", "Önek", "Biçimlendirme", "Değişkenler", "Bu dosyayı doğrudan düzenleyebilirsiniz. Yerel değişiklikler korunur; eksik veya geçersiz girdiler yerleşik İngilizce metni kullanır.", "Değişken adlarını değiştirmeyin ve yalnızca ilgili iletinin değişkenlerini kullanın."};
            case "vi_vi" -> new String[]{"Chỉnh sửa tệp", "Tiền tố", "Định dạng", "Biến", "Bạn có thể chỉnh sửa trực tiếp tệp này. Thay đổi cục bộ được giữ lại; mục thiếu hoặc không hợp lệ dùng bản tiếng Anh tích hợp.", "Giữ nguyên tên biến và chỉ dùng những biến có sẵn trong từng thông báo."};
            case "zh_cn" -> new String[]{"编辑文件", "前缀", "格式", "变量", "可直接编辑此文件。本地修改会保留；缺失或无效条目使用内置英语文本。", "请勿更改变量名，仅使用各消息中原有的变量。"};
            case "zh_tw" -> new String[]{"編輯檔案", "前綴", "格式", "變數", "可直接編輯此檔案。本機修改會保留；缺少或無效的項目使用內建英文文字。", "請勿變更變數名稱，僅使用各訊息中原有的變數。"};
            default -> new String[]{"File editing", "Prefix", "Formatting", "Variables", "Edit this file directly. Local changes are preserved; missing or invalid entries use built-in English.", "Keep variable names unchanged and use only the variables belonging to each message."};
        };
    }

    public record Options(String plugin, String locale, List<String> prefixLines,
                          List<String> formattingLines, Map<String, String> variableDefinitions) {
        public Options {
            plugin = Objects.requireNonNull(plugin, "plugin");
            locale = Objects.requireNonNull(locale, "locale");
            prefixLines = List.copyOf(prefixLines);
            formattingLines = List.copyOf(formattingLines);
            variableDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(variableDefinitions)));
        }
    }
}
