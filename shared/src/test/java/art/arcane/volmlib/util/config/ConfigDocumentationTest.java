package art.arcane.volmlib.util.config;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigDocumentationTest {
    @Test
    public void annotatedImpactEmitsAnEffectLine() throws Exception {
        List<String> lines = comments("annotated", false);

        assertEquals(List.of("Explicit summary.", "Effect: Explicit impact."), lines);
    }

    @Test
    public void annotationWithoutImpactEmitsSummaryOnly() throws Exception {
        List<String> lines = comments("annotatedWithoutImpact", false);

        assertEquals(List.of("Explicit summary."), lines);
    }

    @Test
    public void genericAnnotatedImpactIsSuppressed() throws Exception {
        List<String> lines = comments("annotatedWithGenericImpact", false);

        assertEquals(List.of("Explicit summary."), lines);
    }

    @Test
    public void unannotatedFieldsNeverGainAnEffectLine() throws Exception {
        assertEquals(1, comments("unannotatedFlag", false).size());
        assertEquals(1, comments("unannotatedCooldownMillis", 0L).size());
        assertEquals(1, comments("unannotatedOverrides", new LinkedHashMap<String, String>()).size());
        assertFalse(comments("unannotatedFlag", false).get(0).startsWith("Effect:"));
        assertFalse(comments("unannotatedCooldownMillis", 0L).get(0).startsWith("Effect:"));
        assertFalse(comments("unannotatedOverrides", new LinkedHashMap<String, String>()).get(0).startsWith("Effect:"));
    }

    @Test
    public void curatedPolicyKeepsPlayerFacingSoundControlsVisible() throws Exception {
        Field field = Fixture.class.getDeclaredField("immunitySoundVolume");

        assertTrue(new CuratedConfigExposePolicy().expose("adaptation:nether-ashwalker", "", field, 0D));
    }

    @Test
    public void curatedPolicyHidesAdvancedAndTuningFields() throws Exception {
        CuratedConfigExposePolicy policy = new CuratedConfigExposePolicy();
        Field advanced = Fixture.class.getDeclaredField("advancedKnob");
        Field interval = Fixture.class.getDeclaredField("statIntervalMs");

        assertFalse(policy.expose("core-config", "", advanced, 1));
        assertFalse(policy.expose("core-config", "", interval, 250));
        assertTrue(ConfigExposePolicy.ALL.expose("core-config", "", advanced, 1));
        assertTrue(ConfigExposePolicy.ALL.expose("core-config", "", interval, 250));
    }

    private static List<String> comments(String fieldName, Object value) throws Exception {
        Field field = Fixture.class.getDeclaredField(fieldName);
        return ConfigDocumentation.buildFieldComments("core-config", "fixture", field, value);
    }

    private static final class Fixture {
        @ConfigDoc(value = "Explicit summary.", impact = "Explicit impact.")
        private boolean annotated = false;
        @ConfigDoc(value = "Explicit summary.")
        private boolean annotatedWithoutImpact = false;
        @ConfigDoc(value = "Explicit summary.", impact = "True enables this behavior and false disables it.")
        private boolean annotatedWithGenericImpact = false;
        private boolean unannotatedFlag = false;
        private long unannotatedCooldownMillis = 0;
        private double immunitySoundVolume = 0;
        @ConfigAdvanced
        private int advancedKnob = 1;
        private int statIntervalMs = 250;
        private Map<String, String> unannotatedOverrides = new LinkedHashMap<>();
    }
}
