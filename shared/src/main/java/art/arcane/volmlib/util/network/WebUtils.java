package art.arcane.volmlib.util.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class WebUtils {
    private WebUtils() {
    }

    public static JsonElement getJson(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        if (con.getResponseCode() != 200) {
            throw new IOException("Failed to retrieve JSON data from \"" + url + "\": " + con.getResponseCode());
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                buffer.append(line);
            }

            return new JsonParser().parse(buffer.toString());
        }
    }

    public static void downloadFile(String url, java.io.File target) throws IOException {
        try (InputStream inputStream = new URL(url).openStream()) {
            java.io.File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
