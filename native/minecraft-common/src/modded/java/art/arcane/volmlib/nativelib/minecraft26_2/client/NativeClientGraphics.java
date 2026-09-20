package art.arcane.volmlib.nativelib.minecraft26_2.client;

import art.arcane.volmlib.nativelib.client.ClientGraphics;
import art.arcane.volmlib.nativelib.client.ClientTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public final class NativeClientGraphics implements ClientGraphics {
    private GuiGraphicsExtractor graphics;

    GuiGraphicsExtractor bind(GuiGraphicsExtractor current) {
        GuiGraphicsExtractor previous = graphics;
        graphics = current;
        return previous;
    }

    public int guiWidth() { return graphics.guiWidth(); }
    public int guiHeight() { return graphics.guiHeight(); }
    public int lineHeight() { return Minecraft.getInstance().font.lineHeight; }
    public int textWidth(String text) { return Minecraft.getInstance().font.width(text); }
    public void fill(int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, bottom, color);
    }
    public void text(String text, int x, int y, int color) {
        graphics.text(Minecraft.getInstance().font, text, x, y, color);
    }
    public void blit(ClientTexture texture, int x, int y, int width, int height) {
        NativeClientTexture nativeTexture = (NativeClientTexture) texture;
        graphics.blit(RenderPipelines.GUI_TEXTURED, nativeTexture.id(), x, y, 0.0F, 0.0F,
                width, height, width, height);
    }
}
