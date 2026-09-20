package art.arcane.volmlib.nativelib.minecraft26_2.client;

import art.arcane.volmlib.nativelib.client.ClientTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

record NativeClientTexture(Identifier id) implements ClientTexture {
    public void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getTextureManager().release(id);
        }
    }
}
