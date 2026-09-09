package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BukkitLanguageMessagesTest {
    private static final Pattern DIRECT_PLAYER_MESSAGE = Pattern.compile("message\\(player,\\s*\"");
    private static final Pattern DIRECT_COMPONENT_PROSE = Pattern.compile(
            "ComponentText\\.(?:literal|markup)\\(\"[^\"\\r\\n]*[A-Za-z]{2,}\\s+[A-Za-z]{2,}"
    );

    @Test
    public void catalogContainsEveryDeclaredLanguageSurfaceKey() throws IllegalAccessException {
        Set<String> catalog = new HashSet<>();
        for (MessageKey key : BukkitLanguageMessages.keys()) {
            assertTrue("Duplicate language key " + key.id(), catalog.add(key.id()));
        }
        for (Field field : BukkitLanguageMessages.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && field.getType() == TextKey.class) {
                TextKey key = (TextKey) field.get(null);
                assertTrue("Uncataloged language key " + key.id(), catalog.contains(key.id()));
            }
        }
        assertTrue("Expected the complete picker and editor catalog", catalog.size() >= 65);
    }

    @Test
    public void editorDoesNotEmbedPlayerFacingProse() throws IOException {
        Path source = Path.of(
                "shared", "src", "main", "java", "art", "arcane", "volmlib", "util", "localization",
                "BukkitLanguageEditor.java"
        );
        if (Files.notExists(source)) {
            source = Path.of(
                    "src", "main", "java", "art", "arcane", "volmlib", "util", "localization",
                    "BukkitLanguageEditor.java"
            );
        }
        String text = Files.readString(source);

        assertFalse(DIRECT_PLAYER_MESSAGE.matcher(text).find());
        assertFalse(DIRECT_COMPONENT_PROSE.matcher(text).find());
    }

    @Test
    public void pickerDoesNotEmbedPlayerFacingLanguageProse() throws IOException {
        Path source = Path.of(
                "shared", "src", "main", "java", "art", "arcane", "volmlib", "util", "localization",
                "BukkitLanguageSwitcher.java"
        );
        if (Files.notExists(source)) {
            source = Path.of(
                    "src", "main", "java", "art", "arcane", "volmlib", "util", "localization",
                    "BukkitLanguageSwitcher.java"
            );
        }
        String text = Files.readString(source);
        String picker = text.substring(text.indexOf("private boolean execute(CommandSender"),
                text.indexOf("private void showVolmitHome(CommandSender"));
        String selection = text.substring(text.indexOf("private void select(CommandSender"),
                text.indexOf("private void reply(CommandSender"));

        assertFalse(picker.contains("message(sender, \""));
        assertFalse(selection.contains("message(sender, \""));
    }
}
