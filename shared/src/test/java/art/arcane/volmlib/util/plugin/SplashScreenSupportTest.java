package art.arcane.volmlib.util.plugin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SplashScreenSupportTest {
    @Test
    public void trimsMinecraftSuffixFromProvidedServerVersion() {
        assertEquals("Paper 26.3 build 142", SplashScreenSupport.serverVersionWithoutMcSuffix(
                "Paper 26.3 build 142 (MC: 26.3)"));
    }

    @Test
    public void preservesLoaderDescriptionWithoutMinecraftSuffix() {
        assertEquals("Fabric / Minecraft 26.3", SplashScreenSupport.serverVersionWithoutMcSuffix(
                "Fabric / Minecraft 26.3"));
    }

    @Test
    public void artColorsSwitchOnlyWhenTheGlyphKindChanges() {
        assertEquals("[F]██[E]╗ [F]█[E]╔═╝", SplashScreenSupport.colorizeArt("██╗ █╔═╝", "[F]", "[E]"));
        assertEquals(" [F]██[E]╗ [F]█", SplashScreenSupport.colorizeArt(" ██╗ █", "[F]", "[E]"));
        assertEquals("  ", SplashScreenSupport.colorizeArt("  ", "[F]", "[E]"));
    }

    @Test
    public void releaseTrainExcludesPatchAndPrereleaseSuffix() {
        assertEquals("3.2", SplashScreenSupport.releaseTrain("3.2.1-beta"));
        assertEquals("3", SplashScreenSupport.releaseTrain("3-beta"));
    }
}
