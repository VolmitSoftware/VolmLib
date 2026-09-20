package art.arcane.volmlib.nativelib.client;

public interface ClientGraphics {
    int guiWidth();
    int guiHeight();
    int lineHeight();
    int textWidth(String text);
    void fill(int left, int top, int right, int bottom, int color);
    void text(String text, int x, int y, int color);
    void blit(ClientTexture texture, int x, int y, int width, int height);
}
