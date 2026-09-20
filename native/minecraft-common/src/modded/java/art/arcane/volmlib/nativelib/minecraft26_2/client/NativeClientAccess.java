package art.arcane.volmlib.nativelib.minecraft26_2.client;

import art.arcane.volmlib.nativelib.client.ClientGraphics;
import art.arcane.volmlib.nativelib.client.ClientKeyBinding;
import art.arcane.volmlib.nativelib.client.ClientKeyCategory;
import art.arcane.volmlib.nativelib.client.ClientScreen;
import art.arcane.volmlib.nativelib.client.ClientTexture;
import art.arcane.volmlib.nativelib.client.ClientTextureData;
import art.arcane.volmlib.nativelib.client.ClientToastKind;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Consumer;

public final class NativeClientAccess {
    private static final NativeClientGraphics GRAPHICS = new NativeClientGraphics();

    private NativeClientAccess() {
    }

    public static boolean playerPresent() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null;
    }
    public static boolean guiPresent() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.gui != null;
    }
    public static int playerBlockX() { return Minecraft.getInstance().player.getBlockX(); }
    public static int playerBlockZ() { return Minecraft.getInstance().player.getBlockZ(); }
    public static int targetBlockX() {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos().getX() : playerBlockX();
    }
    public static int targetBlockZ() {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos().getZ() : playerBlockZ();
    }
    public static void render(GuiGraphicsExtractor frame, Consumer<ClientGraphics> renderer) {
        GuiGraphicsExtractor previous = GRAPHICS.bind(frame);
        try {
            renderer.accept(GRAPHICS);
        } finally {
            GRAPHICS.bind(previous);
        }
    }
    public static void openScreen(ClientScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        minecraft.setScreenAndShow(new NativeClientScreen(screen));
    }
    public static void toast(ClientToastKind kind, String title, String body) {
        SystemToast.SystemToastId token = switch (kind) {
            case SUCCESS -> SystemToast.SystemToastId.WORLD_BACKUP;
            case WARNING -> SystemToast.SystemToastId.UNSECURE_SERVER_WARNING;
            case ERROR -> SystemToast.SystemToastId.PACK_LOAD_FAILURE;
            case INFORMATION -> SystemToast.SystemToastId.PERIODIC_NOTIFICATION;
        };
        SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), token,
                Component.literal(title), Component.literal(body));
    }
    public static ClientKeyCategory category(String id) {
        return new Category(KeyMapping.Category.register(Identifier.parse(id)));
    }
    public static ClientKeyBinding key(String translationKey, int defaultKey, ClientKeyCategory category) {
        return new Key(new KeyMapping(translationKey, defaultKey, ((Category) category).handle()));
    }
    public static KeyMapping keyHandle(ClientKeyBinding binding) { return ((Key) binding).handle(); }
    public static ClientTexture upload(ClientTextureData data) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, data.width(), data.height(), false);
        for (int y = 0; y < data.height(); y++) {
            int row = y * data.width();
            for (int x = 0; x < data.width(); x++) {
                int argb = data.argb()[row + x];
                int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >>> 16) & 0xFF);
                image.setPixelABGR(x, y, abgr);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(data.namespace(), data.path());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(data::label, image));
        return new NativeClientTexture(id);
    }

    private record Category(KeyMapping.Category handle) implements ClientKeyCategory {
    }
    private record Key(KeyMapping handle) implements ClientKeyBinding {
        public boolean consumeClick() { return handle.consumeClick(); }
    }
}
