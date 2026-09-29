package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LanguageFileHeaderTest {
    @Test
    public void rendersTheSameFourSectionsWithLocalizedLabels() {
        List<String> english = LanguageFileHeader.render(new LanguageFileHeader.Options(
                "Rift", "en_US", List.of("runtime.prefix is the chat prefix."),
                List.of("Colors use & codes."), Map.of("name", "World name")));
        List<String> japanese = LanguageFileHeader.render(new LanguageFileHeader.Options(
                "Rift", "ja-JP", List.of("runtime.prefix はチャットの接頭辞です。"),
                List.of("色には & コードを使用します。"), Map.of("name", "ワールド名")));

        assertEquals(List.of("=== File editing ===", "=== Prefix ===", "=== Formatting ===", "=== Variables ==="),
                english.stream().filter(line -> line.startsWith("=== ")).toList());
        assertEquals(List.of("=== ファイルの編集 ===", "=== 接頭辞 ===", "=== 書式 ===", "=== 変数 ==="),
                japanese.stream().filter(line -> line.startsWith("=== ")).toList());
        assertTrue(japanese.contains("plugins/Rift/languages/ja-JP.toml"));
        assertTrue(japanese.contains("  {name}  ワールド名"));
    }
}
