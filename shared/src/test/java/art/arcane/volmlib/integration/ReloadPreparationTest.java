package art.arcane.volmlib.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class ReloadPreparationTest {
    @Test
    public void refusesWithoutLosingTheOperatorReason() {
        ReloadPreparation preparation = ReloadPreparation.refuse("  A world save is still active  ");

        assertFalse(preparation.ready());
        assertEquals("A world save is still active", preparation.reason());
    }

    @Test
    public void rejectsMissingOrContradictoryReadinessDetails() {
        assertThrows(NullPointerException.class, () -> ReloadPreparation.refuse(null));
        assertThrows(IllegalArgumentException.class, () -> ReloadPreparation.refuse("   "));
        assertThrows(IllegalArgumentException.class,
                () -> new ReloadPreparation(true, "A world save is still active"));
    }
}
