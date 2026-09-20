package art.arcane.volmlib.nativelib.common.advancement;

import art.arcane.volmlib.nativelib.advancement.AdvancementAccess;
import org.bukkit.Bukkit;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import art.arcane.volmlib.nativelib.common.advancement.MinecraftKeyWrapperImpl;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementDisplayWrapper;
import art.arcane.volmlib.nativelib.common.advancement.advancement.AdvancementDisplayWrapperImpl;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementFrameTypeWrapper;
import art.arcane.volmlib.nativelib.common.advancement.advancement.AdvancementFrameTypeWrapperImpl;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.PreparedAdvancementWrapper;
import art.arcane.volmlib.nativelib.common.advancement.advancement.PreparedAdvancementWrapperImpl;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import art.arcane.volmlib.nativelib.common.advancement.advancement.AdvancementWrapperImpl;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutAdvancementsWrapper;
import art.arcane.volmlib.nativelib.common.advancement.packets.PacketPlayOutAdvancementsWrapperImpl;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutSelectAdvancementTabWrapper;
import art.arcane.volmlib.nativelib.common.advancement.packets.PacketPlayOutSelectAdvancementTabWrapperImpl;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.VanillaAdvancementDisablerWrapper;
import art.arcane.volmlib.nativelib.common.advancement.VanillaAdvancementDisablerWrapperImpl;

public class AdvancementAccessImpl implements AdvancementAccess {
    @Override
    public <T> Class<? extends T> wrapperClass(Class<T> wrapper) {
        if (wrapper == MinecraftKeyWrapper.class) {
            return MinecraftKeyWrapperImpl.class.asSubclass(wrapper);
        }
        if (wrapper == AdvancementDisplayWrapper.class) {
            return AdvancementDisplayWrapperImpl.class.asSubclass(wrapper);
        }
        if (wrapper == AdvancementFrameTypeWrapper.class) {
            return AdvancementFrameTypeWrapperImpl.class.asSubclass(wrapper);
        }
        if (wrapper == PreparedAdvancementWrapper.class) {
            return PreparedAdvancementWrapperImpl.class.asSubclass(wrapper);
        }
        if (wrapper == AdvancementWrapper.class) {
            return AdvancementWrapperImpl.class.asSubclass(wrapper);
        }
        if (wrapper == PacketPlayOutAdvancementsWrapper.class) {
            return PacketPlayOutAdvancementsWrapperImpl.class.asSubclass(wrapper);
        }
        if (wrapper == PacketPlayOutSelectAdvancementTabWrapper.class) {
            return PacketPlayOutSelectAdvancementTabWrapperImpl.class.asSubclass(wrapper);
        }
        if (wrapper == VanillaAdvancementDisablerWrapper.class) {
            return VanillaAdvancementDisablerWrapperImpl.class.asSubclass(wrapper);
        }
        throw new IllegalArgumentException("Unsupported advancement wrapper " + wrapper.getName());
    }

    @Override
    public Class<?> minecraftClass(String name, String minecraftPackage) throws ClassNotFoundException {
        return Class.forName("net.minecraft." + minecraftPackage + '.' + name);
    }

    @Override
    public Class<?> craftClass(String name) throws ClassNotFoundException {
        return Class.forName(Bukkit.getServer().getClass().getPackageName() + '.' + name);
    }
}
