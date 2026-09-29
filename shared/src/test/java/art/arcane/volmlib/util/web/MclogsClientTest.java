package art.arcane.volmlib.util.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MclogsClientTest {
    @Test
    public void publishesTheReportIdentityAsSourceAndVisibleMetadata() {
        JsonObject body = JsonParser.parseString(MclogsClient.requestBody(
                "report", "VolmitSoftware - ShapedPortals - v2.0.0")).getAsJsonObject();

        assertEquals("VolmitSoftware - ShapedPortals - v2.0.0", body.get("source").getAsString());
        JsonArray metadata = body.getAsJsonArray("metadata");
        assertEquals(1, metadata.size());
        assertEquals("Report", metadata.get(0).getAsJsonObject().get("label").getAsString());
        assertEquals("VolmitSoftware - ShapedPortals - v2.0.0",
                metadata.get(0).getAsJsonObject().get("value").getAsString());
        assertTrue(metadata.get(0).getAsJsonObject().get("visible").getAsBoolean());
    }

    @Test
    public void acceptsOnlyTheCanonicalUrlForTheReturnedId() throws IOException {
        assertEquals("https://mclo.gs/WnMMikq", MclogsClient.parseResponse("""
                {"success":true,"id":"WnMMikq","url":"https://mclo.gs/WnMMikq","token":"secret"}
                """).toString());
    }

    @Test
    public void rejectsUnexpectedOrMalformedResponses() {
        IOException unexpected = assertThrows(IOException.class, () -> MclogsClient.parseResponse("""
                {"success":true,"id":"safe","url":"https://example.com/stolen","token":"secret"}
                """));
        assertTrue(unexpected.getMessage().contains("unexpected"));
        IOException malformed = assertThrows(IOException.class, () -> MclogsClient.parseResponse("{"));
        assertTrue(malformed.getMessage().contains("invalid response"));
    }

    @Test
    public void sanitizesServiceErrors() {
        IOException exception = assertThrows(IOException.class, () -> MclogsClient.parseResponse("""
                {"success":false,"error":"bad\nrequest"}
                """));
        assertEquals("mclo.gs rejected the content: bad request", exception.getMessage());
    }
}
