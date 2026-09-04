package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.plugin.ComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class BukkitLanguageSwitcherOptionsTest {
    @Test
    public void supportsPluginOwnedCompactLanguageEditFeedback() {
        PluginLanguageEditor.Options editor = new PluginLanguageEditor.Options(locale -> null, edit -> null);
        BukkitLanguageSwitcher.LanguageEditFeedback feedback = (sender, change) ->
                ComponentText.literal(change.key() + ':' + change.after() + ':' + change.before());
        BukkitLanguageSwitcher.Options options = new BukkitLanguageSwitcher.Options(
                "rift",
                "rift.config",
                DirectorMiniMenu.Theme.adaptRed(),
                DirectorTextResolver.ENGLISH,
                editor,
                feedback
        );
        BukkitLanguageSwitcher.LanguageEditChange change = new BukkitLanguageSwitcher.LanguageEditChange(
                "en_US", "rift.gui.setting_saved", "old", "new");

        assertSame(feedback, options.editorFeedback());
        assertEquals("rift.gui.setting_saved:new:old", options.editorFeedback().saved(null, change).plain());
    }

    @Test
    public void leavesCompactFeedbackDisabledWhenNotConfigured() {
        PluginLanguageEditor.Options editor = new PluginLanguageEditor.Options(locale -> null, edit -> null);
        BukkitLanguageSwitcher.Options options = new BukkitLanguageSwitcher.Options(
                "adapt",
                "adapt.configurator",
                DirectorMiniMenu.Theme.adaptRed(),
                DirectorTextResolver.ENGLISH,
                editor
        );

        assertNull(options.editorFeedback());
    }
}
