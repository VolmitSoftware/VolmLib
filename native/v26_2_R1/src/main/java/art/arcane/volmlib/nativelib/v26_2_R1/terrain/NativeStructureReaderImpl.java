package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.common.structure.ReflectiveStructureReader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class NativeStructureReaderImpl extends ReflectiveStructureReader {
    @Override
    public Session open() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        return session(server.registryAccess(), server.getStructureManager());
    }

    @Override
    public List<String> templates() {
        return templates(((CraftServer) Bukkit.getServer()).getServer().getResourceManager());
    }

    static List<String> templates(ResourceManager resources) {
        TreeSet<String> keys = new TreeSet<>();
        for (Identifier identifier : resources.listResources("structure", location -> location.getPath().endsWith(".nbt")).keySet()) {
            String path = identifier.getPath();
            if (path.startsWith("structure/")) {
                path = path.substring("structure/".length());
            }
            path = path.substring(0, path.length() - ".nbt".length());
            if (!path.isEmpty()) {
                keys.add(identifier.getNamespace() + ":" + path);
            }
        }
        return new ArrayList<>(keys);
    }
}
