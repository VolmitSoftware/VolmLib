package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class LocalizationValidationExceptionTest {
    @Test
    public void reportsTheFirstErrorAndRetainsTheValidationResult() {
        LocalizationIssue first = new LocalizationIssue(
                LocalizationSeverity.ERROR,
                LocalizationIssueCode.MISSING_KEY,
                "fr_FR",
                "menu.title",
                "Missing translation"
        );
        LocalizationIssue second = new LocalizationIssue(
                LocalizationSeverity.ERROR,
                LocalizationIssueCode.SHAPE_MISMATCH,
                "fr_FR",
                "menu.lore",
                "Expected lines"
        );
        LocalizationValidationResult result = new LocalizationValidationResult(List.of(first, second));

        LocalizationValidationException exception = new LocalizationValidationException(result);

        assertSame(result, exception.result());
        assertEquals(
                "Localization validation failed with 2 error(s): MISSING_KEY menu.title Missing translation",
                exception.getMessage()
        );
    }

    @Test
    public void reportsAGenericMessageWhenNoErrorsArePresent() {
        LocalizationValidationException exception = new LocalizationValidationException(
                LocalizationValidationResult.empty()
        );

        assertEquals("Localization validation failed", exception.getMessage());
    }

    @Test
    public void rejectsANullValidationResult() {
        assertThrows(NullPointerException.class, () -> new LocalizationValidationException(null));
    }
}
