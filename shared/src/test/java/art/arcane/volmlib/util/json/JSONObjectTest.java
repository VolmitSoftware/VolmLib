package art.arcane.volmlib.util.json;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JSONObjectTest {
    private PrintStream previousError;
    private ByteArrayOutputStream errors;
    private PrintStream capturedError;

    @Before
    public void captureErrors() {
        previousError = System.err;
        errors = new ByteArrayOutputStream();
        capturedError = new PrintStream(errors, true, StandardCharsets.UTF_8);
        System.setErr(capturedError);
    }

    @After
    public void restoreErrors() {
        System.setErr(previousError);
        capturedError.close();
    }

    @Test
    public void absentBooleanUsesDefaultWithoutLogging() {
        JSONObject object = new JSONObject();

        assertFalse(object.optBoolean("missing"));
        assertFalse(object.optBoolean("missing", false));
        assertTrue(object.optBoolean("missing", true));
        assertFalse(object.has("missing"));
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
        assertThrows(JSONException.class, () -> object.getBoolean("missing"));
    }

    @Test
    public void booleanAndCaseInsensitiveStringValuesOverrideDefault() {
        JSONObject object = new JSONObject();
        object.put("booleanTrue", true);
        object.put("booleanFalse", false);
        object.put("stringTrue", "TrUe");
        object.put("stringFalse", "FaLsE");

        assertTrue(object.optBoolean("booleanTrue", false));
        assertFalse(object.optBoolean("booleanFalse", true));
        assertTrue(object.optBoolean("stringTrue", false));
        assertFalse(object.optBoolean("stringFalse", true));
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void malformedPresentValuesKeepDefaultAndStackTrace() {
        Object[] invalidValues = {Integer.valueOf(1), "yes", " true ", JSONObject.NULL, new JSONObject(), new JSONArray()};
        for (Object invalid : invalidValues) {
            JSONObject object = new JSONObject().put("invalid", invalid);
            for (boolean defaultValue : new boolean[]{false, true}) {
                errors.reset();

                assertEquals(defaultValue, object.optBoolean("invalid", defaultValue));

                String error = errors.toString(StandardCharsets.UTF_8);
                assertTrue(error.contains("JSONException: JSONObject[\"invalid\"] is not a Boolean."));
                assertTrue(error.contains("at art.arcane.volmlib.util.json.JSONObject.getBoolean("));
            }
            assertThrows(JSONException.class, () -> object.getBoolean("invalid"));
        }
    }

    @Test
    public void nullKeyKeepsDefaultAndReportsInvalidCall() {
        JSONObject object = new JSONObject();

        assertTrue(object.optBoolean(null, true));

        String error = errors.toString(StandardCharsets.UTF_8);
        assertTrue(error.contains("JSONException: Null key."));
        assertTrue(error.contains("at art.arcane.volmlib.util.json.JSONObject.get("));
    }
}
