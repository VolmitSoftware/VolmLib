package art.arcane.volmit.packaging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

final class MixinResourceRoots {
    private MixinResourceRoots() {
    }

    static Set<String> read(JarFile jar) throws IOException {
        Set<String> configurations = new LinkedHashSet<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            String basename = name.substring(name.lastIndexOf('/') + 1);
            if (!entry.isDirectory() && (name.endsWith(".mixins.json")
                    || basename.startsWith("mixins.") && name.endsWith(".json"))) {
                configurations.add(name);
            }
        }
        Manifest manifest = jar.getManifest();
        if (manifest != null) {
            String declared = manifest.getMainAttributes().getValue("MixinConfigs");
            if (declared != null) {
                for (String name : declared.split(",")) {
                    if (!name.isBlank()) {
                        configurations.add(name.trim());
                    }
                }
            }
        }
        if (jar.getJarEntry("fabric.mod.json") != null) {
            JsonObject metadata = readObject(jar, "fabric.mod.json");
            if (metadata.has("mixins")) {
                try {
                    for (JsonElement mixin : metadata.getAsJsonArray("mixins")) {
                        configurations.add(mixin.isJsonObject()
                                ? mixin.getAsJsonObject().get("config").getAsString() : mixin.getAsString());
                    }
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid mixin declarations in fabric.mod.json", exception);
                }
            }
        }
        Set<String> roots = new LinkedHashSet<>();
        for (String configuration : configurations) {
            readConfiguration(jar, configuration, roots);
        }
        return roots;
    }

    private static void readConfiguration(JarFile jar, String configuration, Set<String> roots) throws IOException {
        JsonObject json = readObject(jar, configuration);
        try {
            String packageName = json.has("package") ? json.get("package").getAsString() : "";
            String prefix = packageName.isEmpty() ? "" : packageName + ".";
            for (String field : new String[]{"mixins", "client", "server"}) {
                if (!json.has(field)) {
                    continue;
                }
                JsonArray classes = json.getAsJsonArray(field);
                for (JsonElement entry : classes) {
                    roots.add((prefix + entry.getAsString()).replace('.', '/'));
                }
            }
            if (json.has("plugin")) {
                roots.add(json.get("plugin").getAsString().replace('.', '/'));
            }
        } catch (RuntimeException exception) {
            throw new IOException("Invalid mixin configuration " + configuration, exception);
        }
    }

    private static JsonObject readObject(JarFile jar, String resource) throws IOException {
        JarEntry entry = jar.getJarEntry(resource);
        if (entry == null) {
            throw new IOException("Missing mixin resource " + resource);
        }
        try (InputStream input = jar.getInputStream(entry);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid JSON resource " + resource, exception);
        }
    }
}
