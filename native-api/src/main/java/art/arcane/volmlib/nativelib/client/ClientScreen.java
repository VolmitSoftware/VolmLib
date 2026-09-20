package art.arcane.volmlib.nativelib.client;

public interface ClientScreen {
    String title();
    void init(int width, int height);
    void render(ClientGraphics graphics, int mouseX, int mouseY, float partialTick);
    boolean mouseDragged(double dragX, double dragY);
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);
    void removed();
}
