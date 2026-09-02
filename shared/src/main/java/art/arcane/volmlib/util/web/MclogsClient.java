package art.arcane.volmlib.util.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class MclogsClient {
    private static final URI ENDPOINT = URI.create("https://api.mclo.gs/1/log");
    private static final int MAXIMUM_CONTENT_BYTES = 512 * 1024;
    private static final int MAXIMUM_CONTENT_LINES = 5_000;
    private static final int MAXIMUM_RESPONSE_BYTES = 64 * 1024;

    private final HttpClient client;

    public MclogsClient() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5L))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public URI publish(String content, String source, String userAgent) throws IOException, InterruptedException {
        String report = Objects.requireNonNull(content, "content");
        validateSize(report);
        JsonObject body = new JsonObject();
        body.addProperty("content", report);
        body.addProperty("source", sanitizeHeader(source, "VolmLib"));
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(10L))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", sanitizeHeader(userAgent, "VolmLib"))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        String responseBody;
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
            if (bytes.length > MAXIMUM_RESPONSE_BYTES) {
                throw new IOException("mclo.gs response exceeded the 64 KiB limit");
            }
            responseBody = new String(bytes, StandardCharsets.UTF_8);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("mclo.gs returned HTTP " + response.statusCode());
        }
        return parseResponse(responseBody);
    }

    static URI parseResponse(String raw) throws IOException {
        try {
            JsonObject response = JsonParser.parseString(Objects.requireNonNullElse(raw, "")).getAsJsonObject();
            if (!response.has("success") || !response.get("success").getAsBoolean()) {
                String error = response.has("error") ? response.get("error").getAsString() : "unknown service error";
                throw new IOException("mclo.gs rejected the content: " + sanitizeMessage(error));
            }
            if (!response.has("id")) {
                throw new IOException("mclo.gs response did not contain a report id");
            }
            String id = response.get("id").getAsString();
            if (!id.matches("[A-Za-z0-9]+")) {
                throw new IOException("mclo.gs response contained an invalid report id");
            }
            URI expected = URI.create("https://mclo.gs/" + id);
            if (response.has("url") && !expected.toString().equals(response.get("url").getAsString())) {
                throw new IOException("mclo.gs response contained an unexpected report URL");
            }
            return expected;
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("mclo.gs returned an invalid response", exception);
        }
    }

    private static void validateSize(String report) throws IOException {
        if (report.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_CONTENT_BYTES) {
            throw new IOException("Content exceeds the mclo.gs client limit of 512 KiB");
        }
        if (report.lines().limit(MAXIMUM_CONTENT_LINES + 1L).count() > MAXIMUM_CONTENT_LINES) {
            throw new IOException("Content exceeds the mclo.gs client limit of 5,000 lines");
        }
    }

    private static String sanitizeHeader(String value, String fallback) {
        String source = Objects.requireNonNullElse(value, fallback);
        StringBuilder builder = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            builder.append(character < 32 || character > 126 ? '_' : character);
        }
        String sanitized = builder.toString().trim();
        if (sanitized.isBlank()) {
            return fallback;
        }
        return sanitized.length() <= 128 ? sanitized : sanitized.substring(0, 128);
    }

    private static String sanitizeMessage(String value) {
        String source = Objects.requireNonNullElse(value, "unknown service error");
        StringBuilder builder = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            builder.append(Character.isISOControl(character) ? ' ' : character);
        }
        String sanitized = builder.toString().trim();
        return sanitized.length() <= 200 ? sanitized : sanitized.substring(0, 200);
    }
}
