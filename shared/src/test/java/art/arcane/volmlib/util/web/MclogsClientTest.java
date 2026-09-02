package art.arcane.volmlib.util.web;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MclogsClientTest {
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
