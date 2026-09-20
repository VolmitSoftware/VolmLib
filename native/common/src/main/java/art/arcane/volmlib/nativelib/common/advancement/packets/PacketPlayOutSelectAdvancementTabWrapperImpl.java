package art.arcane.volmlib.nativelib.common.advancement.packets;

import art.arcane.volmlib.nativelib.common.advancement.Util;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutSelectAdvancementTabWrapper;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.resources.Identifier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PacketPlayOutSelectAdvancementTabWrapperImpl extends PacketPlayOutSelectAdvancementTabWrapper {
    private final ClientboundSelectAdvancementsTabPacket packet;

    public PacketPlayOutSelectAdvancementTabWrapperImpl() {
        this.packet = new ClientboundSelectAdvancementsTabPacket((Identifier) null);
    }

    public PacketPlayOutSelectAdvancementTabWrapperImpl(@NotNull MinecraftKeyWrapper key) {
        this.packet = new ClientboundSelectAdvancementsTabPacket((Identifier) key.toNMS());
    }

    @Override
    public void sendTo(@NotNull Player player) {
        Util.sendTo(player, packet);
    }
}
