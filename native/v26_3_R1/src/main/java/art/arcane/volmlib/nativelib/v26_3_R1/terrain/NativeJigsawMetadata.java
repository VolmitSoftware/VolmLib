package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.JigsawSourceMetadata;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureTemplatePoolBounds;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class NativeJigsawMetadata {
    private NativeJigsawMetadata() {
    }

    public static JigsawSourceMetadata sourceMetadata(RegistryAccess registryAccess,
                                                       StructureTemplateManager templateManager,
                                                       JigsawStructure source) {
        CompoundTag sourceTag = encodeJigsaw(registryAccess, source);
        return new JigsawSourceMetadata(
                sourceDistance(sourceTag, "horizontal"),
                horizontalReferenceExpansion(source),
                NativeStructureTemplatePoolBounds.sourceHorizontalSpan(
                        registryAccess, templateManager, source));
    }

    public static int templatePoolHorizontalSpan(RegistryAccess registryAccess,
                                                 StructureTemplateManager templateManager,
                                                 String templatePoolKey) {
        return NativeStructureTemplatePoolBounds.horizontalSpan(
                registryAccess, templateManager, templatePoolKey);
    }

    public static int jigsawStartPoolHorizontalSpan(RegistryAccess registryAccess,
                                                    StructureTemplateManager templateManager,
                                                    JigsawStructure source,
                                                    String templatePoolKey) {
        return NativeStructureTemplatePoolBounds.horizontalSpan(
                registryAccess, templateManager, source, templatePoolKey);
    }

    public static CompoundTag encodeJigsaw(RegistryOps<Tag> registryOps,
                                             JigsawStructure source) {
        Tag encoded = Structure.DIRECT_CODEC.encodeStart(registryOps, source).getOrThrow();
        if (!(encoded instanceof CompoundTag structureTag)) {
            throw new IllegalStateException("Native jigsaw codec did not produce a compound");
        }
        return structureTag;
    }

    public static CompoundTag encodeJigsaw(RegistryAccess registryAccess,
                                             JigsawStructure source) {
        return encodeJigsaw(RegistryOps.create(NbtOps.INSTANCE, registryAccess), source);
    }

    public static int sourceDistance(CompoundTag tag, String axis) {
        Tag raw = tag.get("max_distance_from_center");
        if (raw instanceof CompoundTag compound) {
            return compound.getIntOr(axis, 128);
        }
        return tag.getIntOr("max_distance_from_center", 128);
    }

    public static int horizontalReferenceExpansion(Structure structure) {
        BoundingBox content = new BoundingBox(0, 0, 0, 0, 0, 0);
        BoundingBox adjusted = structure.adjustBoundingBox(content);
        int expansion = Math.max(content.minX() - adjusted.minX(), adjusted.maxX() - content.maxX());
        expansion = Math.max(expansion, content.minZ() - adjusted.minZ());
        return Math.max(0, Math.max(expansion, adjusted.maxZ() - content.maxZ()));
    }
}
