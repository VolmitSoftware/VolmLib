package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.util.List;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class LanguageReferenceRendererTest {
    @Test
    public void rendersNestedTomlInCatalogOrder() {
        LinkedHashMap<String, String> forms = new LinkedHashMap<>();
        forms.put("one", "{count} block");
        forms.put("other", "{count} blocks");
        MessageCatalog catalog = MessageCatalog.builder("en_US")
                .add(TextKey.of("command.reload.success", "Reloaded {count} files."))
                .add(LinesKey.of("gui.help.lines", "First", "Second"))
                .add(PluralKey.of("portal.blocks", "count", forms))
                .build();

        String rendered = LanguageReferenceRenderer.render(catalog, List.of("Reference: en_US"));

        assertEquals(
                "# Reference: en_US\n"
                        + "\n[command.reload]\n"
                        + "success = \"Reloaded {count} files.\"\n"
                        + "\n[gui.help]\n"
                        + "lines = [\"First\", \"Second\"]\n"
                        + "\n[portal.blocks]\n"
                        + "one = \"{count} block\"\n"
                        + "other = \"{count} blocks\"\n",
                rendered
        );
    }
}
