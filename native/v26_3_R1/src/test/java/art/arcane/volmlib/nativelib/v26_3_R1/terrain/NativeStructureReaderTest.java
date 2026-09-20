package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.Test;
import org.junit.BeforeClass;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import art.arcane.volmlib.nativelib.terrain.NativeStructureReader;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureReaderTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void preservesConnectorMetadataAfterNativeInfoRecordRemoval() {
        StructureTemplate.JigsawBlockInfo connector = new StructureTemplate.JigsawBlockInfo(
                new BlockPos(4, 7, 9), Blocks.JIGSAW.defaultBlockState(), JigsawBlockEntity.JointType.ALIGNED,
                Identifier.parse("example:name"), ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.parse("example:pool")),
                Identifier.parse("example:target"), 3, 8);
        NativeStructureReader.ConnectorData data = NativeStructureReaderImpl.connectorData(connector, "minecraft:oak_planks");
        assertEquals(4, data.x());
        assertEquals(7, data.y());
        assertEquals(9, data.z());
        assertEquals("example:pool", data.pool());
        assertEquals("example:name", data.name());
        assertEquals("example:target", data.target());
        assertEquals(new NativeStructureReader.ConnectorMetadata("minecraft:oak_planks", 8, 3), data.metadata());
    }

    @Test
    public void restoresNbtFinalStateForSingleAndCompositeElements() throws Exception {
        StructureTemplateManager manager = mock(StructureTemplateManager.class);
        StructureTemplate template = mock(StructureTemplate.class);
        Identifier key = Identifier.parse("example:template");
        when(manager.getOrCreate(key)).thenReturn(template);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("final_state", "minecraft:oak_planks");
        StructureTemplate.StructureBlockInfo block = new StructureTemplate.StructureBlockInfo(new BlockPos(1, 2, 3), Blocks.JIGSAW.defaultBlockState(), nbt);
        StructureTemplate.JigsawBlockInfo connector = new StructureTemplate.JigsawBlockInfo(block.pos(), block.state(),
                JigsawBlockEntity.JointType.ALIGNED, Identifier.parse("example:name"),
                ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.parse("example:pool")), Identifier.parse("example:target"), 2, 4);
        when(template.filterBlocks(eq(BlockPos.ZERO), any(), eq(Blocks.JIGSAW))).thenReturn(List.of(block));
        when(template.getJigsaws(BlockPos.ZERO, Rotation.NONE)).thenAnswer(invocation -> new ArrayList<>(List.of(connector)));
        StructurePoolElement single = StructurePoolElement.single(key.toString()).apply(StructureTemplatePool.Projection.RIGID);
        ListPoolElement composite = new ListPoolElement(List.of(single), StructureTemplatePool.Projection.RIGID);
        NativeStructureReaderImpl reader = new NativeStructureReaderImpl();
        for (StructurePoolElement element : List.of(single, composite)) {
            NativeStructureReader.ConnectorData data = reader.readConnectors(element, manager, () -> 17).connectors().getFirst().read();
            assertEquals(new NativeStructureReader.ConnectorMetadata("minecraft:oak_planks", 4, 2), data.metadata());
        }
    }

    @Test
    public void listsOnlyNbtResourcesWithSortedCanonicalTemplateKeys() {
        ResourceManager resources = mock(ResourceManager.class);
        when(resources.listResources(eq("structure"), any())).thenAnswer(invocation -> {
            ResourceManager.Selector selector = invocation.getArgument(1);
            Map<Identifier, Resource> found = new LinkedHashMap<>();
            for (String key : List.of("z:structure/village/house.nbt", "a:structure/start.nbt", "a:structure/readme.txt", "a:structure/.nbt")) {
                Identifier identifier = Identifier.parse(key);
                if (selector.isIncluded(identifier)) {
                    found.put(identifier, mock(Resource.class));
                }
            }
            return found;
        });
        assertEquals(List.of("a:start", "z:village/house"), NativeStructureReaderImpl.templates(resources));
    }
}
