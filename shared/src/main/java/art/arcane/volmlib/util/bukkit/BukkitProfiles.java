package art.arcane.volmlib.util.bukkit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class BukkitProfiles {
    private BukkitProfiles() {
    }

    public static PlayerProfile forPlayer(Player player) {
        Player requiredPlayer = Objects.requireNonNull(player, "player");
        Object result;
        try {
            result = PlayerProfileAccess.METHOD.invoke(requiredPlayer);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("The server could not read the player's current profile", failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access the player's current profile", failure);
        }
        if (result instanceof PlayerProfile profile) {
            return profile;
        }
        throw new IllegalStateException("The server did not return a Bukkit player profile");
    }

    public static PlayerProfile textureValue(String base64) {
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        applyTextureValue(textures, base64);
        profile.setTextures(textures);
        return profile;
    }

    static void applyTextureValue(PlayerTextures textures, String base64) {
        String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject textureData = root.getAsJsonObject("textures");
        if (textureData == null) {
            throw new IllegalArgumentException("Skull texture value must contain a textures object");
        }
        JsonObject skin = textureData.getAsJsonObject("SKIN");
        if (skin != null) {
            PlayerTextures.SkinModel model = PlayerTextures.SkinModel.CLASSIC;
            JsonObject metadata = skin.getAsJsonObject("metadata");
            if (metadata != null && metadata.has("model") && "slim".equalsIgnoreCase(string(metadata, "model"))) {
                model = PlayerTextures.SkinModel.SLIM;
            }
            textures.setSkin(url(string(skin, "url")), model);
        }
        JsonObject cape = textureData.getAsJsonObject("CAPE");
        if (cape != null) {
            textures.setCape(url(string(cape, "url")));
        }
    }

    public static PlayerProfile textureId(String id) {
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        textures.setSkin(url("https://textures.minecraft.net/texture/" + id));
        profile.setTextures(textures);
        return profile;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Skull texture value must contain string '" + key + "'");
        }
        return value.getAsString();
    }

    private static URL url(String value) {
        try {
            return URI.create(value).toURL();
        } catch (MalformedURLException failure) {
            throw new IllegalArgumentException("Invalid skull texture URL: " + value, failure);
        }
    }

    private static final class PlayerProfileAccess {
        private static final Method METHOD = resolve();

        private static Method resolve() {
            try {
                return Player.class.getMethod("getPlayerProfile");
            } catch (NoSuchMethodException failure) {
                throw new IllegalStateException("The server does not expose current player profiles", failure);
            }
        }
    }
}
