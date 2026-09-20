package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.List;
import java.util.Optional;

public final class NativeStructureQueries {
    private final ServerLevel world;
    private final Registry<Structure> registry;

    public NativeStructureQueries(NativeWorld world) {
        this.world = (ServerLevel) world.nativeHandle();
        this.registry = this.world.getServer().registryAccess().lookupOrThrow(Registries.STRUCTURE);
    }

    public List<String> keys() {
        return registry.keySet().stream().map(Identifier::toString).toList();
    }

    public Optional<Reference> resolve(String key) {
        Identifier identifier = Identifier.tryParse(key);
        return identifier == null ? Optional.empty() : registry.get(identifier).map(holder -> new Reference(identifier.toString(), holder));
    }

    public boolean biomeReachable(Reference reference) {
        return world.getChunkSource().getGenerator() instanceof NativeStructureReachability generator
                && generator.isNativeStructureReachable(reference.holder);
    }

    public boolean hasPlacement(Reference reference) {
        return !world.getChunkSource().getGeneratorState().getPlacementsForStructure(reference.holder).isEmpty();
    }

    public NativeBlockPoint locate(Reference reference, NativeBlockPoint origin, int radius) {
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        Pair<BlockPos, Holder<Structure>> result = generator.findNearestMapStructure(world,
                HolderSet.direct(reference.holder), new BlockPos(origin.x(), origin.y(), origin.z()), radius, false);
        if (result == null) { return null; }
        BlockPos point = result.getFirst();
        return new NativeBlockPoint(point.getX(), point.getY(), point.getZ());
    }

    public int surfaceY(int blockX, int blockZ) {
        world.getChunk(blockX >> 4, blockZ >> 4);
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
    }

    public void loadChunk(int chunkX, int chunkZ) {
        world.getChunk(chunkX, chunkZ);
    }

    public static final class Reference {
        private final String key;
        private final Holder.Reference<Structure> holder;

        private Reference(String key, Holder.Reference<Structure> holder) {
            this.key = key;
            this.holder = holder;
        }

        public String key() { return key; }
        public boolean emptyBiomeFilter() { return holder.value().biomes().stream().findAny().isEmpty(); }
    }
}
