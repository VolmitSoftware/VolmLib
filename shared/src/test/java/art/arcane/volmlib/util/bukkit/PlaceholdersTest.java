package art.arcane.volmlib.util.bukkit;

import org.bukkit.entity.Player;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaceholdersTest {
    @Test
    public void containsPlaceholder_isTheCheapGuardHoloUiCallsPerLine() {
        assertFalse(Placeholders.containsPlaceholder(null));
        assertFalse(Placeholders.containsPlaceholder(""));
        assertFalse(Placeholders.containsPlaceholder("Balance"));
        assertTrue(Placeholders.containsPlaceholder("%vault_eco_balance%"));
        assertTrue(Placeholders.containsPlaceholder("Balance: %vault_eco_balance%"));
    }

    @Test
    public void setPlaceholders_skipsTheReflectiveInvokeWhenThereIsNothingToResolve() {
        String text = "Balance";

        assertSame(text, Placeholders.setPlaceholders(new StubPlayer(), text));
    }

    @Test
    public void setPlaceholders_isTotalAndServesTheTextUnresolvedWhenPlaceholderApiIsAbsent() {
        String text = "Balance: %vault_eco_balance%";

        assertSame(text, Placeholders.setPlaceholders(new StubPlayer(), text));
        assertSame(text, Placeholders.setPlaceholders(new StubPlayer(), text));
    }

    @Test
    public void setPlaceholders_passesNullsThrough() {
        assertNull(Placeholders.setPlaceholders(new StubPlayer(), null));
        assertSame("%a%", Placeholders.setPlaceholders(null, "%a%"));
    }

    private static final class StubPlayer implements Player {
    }
}
