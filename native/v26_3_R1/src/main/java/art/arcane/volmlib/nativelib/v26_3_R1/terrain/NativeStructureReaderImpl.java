package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.common.structure.ReflectiveStructureReader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class NativeStructureReaderImpl extends ReflectiveStructureReader {
    @Override
    public Session open() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        return session(server.registryAccess(), server.getStructureTemplateManager());
    }

    @Override
    public List<String> templates() {
        return templates(((CraftServer) Bukkit.getServer()).getServer().getResourceManager());
    }

    @Override
    protected ConnectorSet readConnectors(Object value, Object templates, LongSupplier randomSeed) throws Exception {
        StructurePoolElement element = (StructurePoolElement) value;
        StructureTemplateManager manager = (StructureTemplateManager) templates;
        Map<BlockPos, String> finalStates = new HashMap<>();
        StructurePoolElement metadataElement = element;
        while (metadataElement instanceof ListPoolElement composite) {
            metadataElement = composite.getElements().getFirst();
        }
        if (metadataElement instanceof SinglePoolElement single) {
            Method getTemplate = SinglePoolElement.class.getDeclaredMethod("getTemplate", StructureTemplateManager.class);
            getTemplate.setAccessible(true);
            StructureTemplate template = (StructureTemplate) getTemplate.invoke(single, manager);
            for (StructureTemplate.StructureBlockInfo block : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings().setRotation(Rotation.NONE), Blocks.JIGSAW)) {
                if (block.nbt() != null) {
                    finalStates.put(block.pos(), block.nbt().getString("final_state").orElse(null));
                }
            }
        }
        List<StructureTemplate.JigsawBlockInfo> values = element.getShuffledJigsawBlocks(manager, BlockPos.ZERO, Rotation.NONE, RandomSource.create(randomSeed.getAsLong()));
        List<Connector> connectors = new ArrayList<>(values.size());
        for (StructureTemplate.JigsawBlockInfo connector : values) {
            ConnectorData data = connectorData(connector, finalStates.get(connector.pos()));
            connectors.add(() -> data);
        }
        return new ConnectorSet(ConnectorStatus.AVAILABLE, connectors);
    }

    static ConnectorData connectorData(StructureTemplate.JigsawBlockInfo connector, String finalState) {
        BlockPos position = connector.pos();
        return new ConnectorData(position.getX(), position.getY(), position.getZ(),
                JigsawBlock.getFrontFacing(connector.state()).getName(), JigsawBlock.getTopFacing(connector.state()).getName(),
                connector.pool().identifier().toString(), connector.name().toString(), connector.target().toString(),
                connector.jointType().toString(), new ConnectorMetadata(finalState, connector.selectionPriority(), connector.placementPriority()));
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
