package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeDevelopmentTargetsTest {
    @Test
    void supportedProvidersHavePublishedBundleMetadata() {
        for (String adapter : List.of("v1_21_R7", "v26_2_R1", "v26_3_R1")) {
            NativeDevelopmentTargets.Target target = NativeDevelopmentTargets.require(adapter);
            assertFalse(target.paperBundle().isBlank());
            assertEquals(25, target.javaVersion());
        }
    }

    @Test
    void unknownProviderCannotBorrowAnotherBundle() {
        assertThrows(IllegalArgumentException.class, () -> NativeDevelopmentTargets.require("v26_9_R1"));
    }
}
