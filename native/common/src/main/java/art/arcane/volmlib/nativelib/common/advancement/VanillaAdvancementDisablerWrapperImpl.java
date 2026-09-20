package art.arcane.volmlib.nativelib.common.advancement;

import art.arcane.volmlib.nativelib.common.advancement.packets.PacketPlayOutAdvancementsWrapperImpl;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.VanillaAdvancementDisablerWrapper;
import com.google.common.collect.ImmutableMap;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class VanillaAdvancementDisablerWrapperImpl extends VanillaAdvancementDisablerWrapper {
    private static final Field FIRST_PACKET = firstPacketField();

    private VanillaAdvancementDisablerWrapperImpl() {
    }

    public static void disableVanillaAdvancements(boolean vanillaAdvancements,
            boolean vanillaRecipeAdvancements) throws IllegalAccessException {
        ServerAdvancementManager manager = ((CraftServer) Bukkit.getServer()).getServer().getAdvancements();
        if (manager.advancements.isEmpty()) {
            return;
        }
        AdvancementTree tree = manager.tree();
        Set<Identifier> locations = new HashSet<>();
        ImmutableMap.Builder<Identifier, AdvancementHolder> retained = ImmutableMap.builder();
        for (Map.Entry<Identifier, AdvancementHolder> entry : manager.advancements.entrySet()) {
            Identifier key = entry.getKey();
            boolean recipe = key.getPath().startsWith("recipes/");
            if (key.getNamespace().equals("minecraft")
                    && ((vanillaAdvancements && !recipe) || (vanillaRecipeAdvancements && recipe))) {
                locations.add(key);
            } else {
                retained.put(key, entry.getValue());
            }
        }
        Set<Identifier> removed = descendants(tree, locations);
        manager.advancements = retained.buildOrThrow();
        tree.remove(locations);
        ClientboundUpdateAdvancementsPacket packet = PacketPlayOutAdvancementsWrapperImpl.removalPacket(removed);
        for (Player player : Bukkit.getOnlinePlayers()) {
            ServerPlayer handle = ((CraftPlayer) player).getHandle();
            PlayerAdvancements advancements = handle.getAdvancements();
            advancements.reload(manager);
            FIRST_PACKET.setBoolean(advancements, false);
            handle.connection.send(packet);
        }
    }

    private static Set<Identifier> descendants(AdvancementTree tree, Set<Identifier> roots) {
        Set<Identifier> removed = new HashSet<>();
        ArrayDeque<AdvancementNode> pending = new ArrayDeque<>();
        for (Identifier id : roots) {
            AdvancementNode node = tree.get(id);
            if (node != null) {
                pending.add(node);
            }
        }
        while (!pending.isEmpty()) {
            AdvancementNode node = pending.removeFirst();
            if (!removed.add(node.holder().id())) {
                continue;
            }
            for (AdvancementNode child : node.children()) {
                pending.addLast(child);
            }
        }
        return removed;
    }

    private static Field firstPacketField() {
        try {
            Field field = PlayerAdvancements.class.getDeclaredField("isFirstPacket");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
