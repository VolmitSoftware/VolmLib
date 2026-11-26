package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.Vector3d;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.util.List;

@NativeBinding("terrain.NativeBlockAccessImpl")
public interface NativeBlockAccess extends NativeChunkAccess {
    boolean hasTile(Material material);

    boolean hasTile(Location location);

    String getEntitySpawnCategory(String key);

    KMap<String, Object> serializeTile(Location location);

    void deserializeTile(KMap<String, Object> data, Location location, TileWriteScheduler scheduler);

    ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt);

    Vector3d getBoundingbox(EntityType entity);

    Entity spawnEntity(Location location, EntityType type, CreatureSpawnEvent.SpawnReason reason);

    Color getBiomeColor(Location location, BiomeColor type);

    KMap<Material, List<BlockProperty>> getBlockProperties();

    /**
     * Whether the server's own canSurvive rule lets {@code plant} stand on {@code ground}. Bindings that cannot ask
     * the server let every plant stand, the same answer a rule that fails to answer gives.
     */
    default boolean canSurviveOn(BlockData plant, BlockData ground) {
        return true;
    }

    /** Drops the canSurviveOn answers remembered so far, which a resource reload may have changed. */
    default void forgetBlockRules() {
    }
}
