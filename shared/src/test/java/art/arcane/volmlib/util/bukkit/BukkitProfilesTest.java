package art.arcane.volmlib.util.bukkit;

import org.bukkit.profile.PlayerTextures;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class BukkitProfilesTest {
    @Test
    public void returnsTheExistingPlayerProfileWithoutRebuildingOrUpdatingIt() {
        Player player = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(player.getPlayerProfile()).thenReturn(profile);
        assertSame(profile, BukkitProfiles.forPlayer(player));
        verifyNoInteractions(profile);
    }

    @Test
    public void currentProfileFailureRetainsItsCause() {
        Player player = mock(Player.class);
        IllegalArgumentException rejected = new IllegalArgumentException("profile unavailable");
        when(player.getPlayerProfile()).thenThrow(rejected);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> BukkitProfiles.forPlayer(player));
        assertSame(rejected, failure.getCause());
    }

    @Test
    public void appliesSkinCapeAndSlimModelFromTheTexturePayload() throws Exception {
        PlayerTextures textures = mock(PlayerTextures.class);
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/skin\","
                + "\"metadata\":{\"model\":\"slim\"}},\"CAPE\":{\"url\":\"https://textures.minecraft.net/texture/cape\"}}}";
        BukkitProfiles.applyTextureValue(textures, encode(json));
        verify(textures).setSkin(URI.create("https://textures.minecraft.net/texture/skin").toURL(), PlayerTextures.SkinModel.SLIM);
        verify(textures).setCape(URI.create("https://textures.minecraft.net/texture/cape").toURL());
    }

    @Test
    public void optionalMetadataWithoutAModelUsesClassicSkin() throws Exception {
        PlayerTextures textures = mock(PlayerTextures.class);
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/skin\",\"metadata\":{}}}}";
        BukkitProfiles.applyTextureValue(textures, encode(json));
        verify(textures).setSkin(URI.create("https://textures.minecraft.net/texture/skin").toURL(), PlayerTextures.SkinModel.CLASSIC);
    }

    @Test
    public void missingTexturesAreRejectedBeforeApplyingAnything() {
        PlayerTextures textures = mock(PlayerTextures.class);
        assertThrows(IllegalArgumentException.class, () -> BukkitProfiles.applyTextureValue(textures, encode("{}")));
        verifyNoInteractions(textures);
    }

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
