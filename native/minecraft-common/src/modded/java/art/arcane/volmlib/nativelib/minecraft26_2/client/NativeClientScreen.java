package art.arcane.volmlib.nativelib.minecraft26_2.client;

import art.arcane.volmlib.nativelib.client.ClientScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class NativeClientScreen extends Screen {
    private final ClientScreen content;
    private final NativeClientGraphics graphics = new NativeClientGraphics();

    NativeClientScreen(ClientScreen content) {
        super(Component.literal(content.title()));
        this.content = content;
    }

    protected void init() { content.init(width, height); }
    public boolean isPauseScreen() { return false; }
    public void extractRenderState(GuiGraphicsExtractor frame, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor previous = graphics.bind(frame);
        try {
            content.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.bind(previous);
        }
    }
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The map itself has nothing clickable, but swallowing every click also swallows the ones widgets and
        // the parent screen need. Let Screen route it; drag and scroll are handled below.
        return super.mouseClicked(event, doubleClick);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return content.mouseDragged(dragX, dragY);
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return content.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    public void removed() {
        content.removed();
        super.removed();
    }
}
