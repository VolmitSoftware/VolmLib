package art.arcane.volmlib.nativelib.common.entity;

import art.arcane.volmlib.nativelib.entity.EntityMetadataAccess;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;

public class NativeEntityMetadataAccess implements EntityMetadataAccess {
    private static final VarHandle ITEMS = items();
    private static final long OFFSET = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    public long fingerprint(Entity entity) {
        SynchedEntityData data = ((CraftEntity) Objects.requireNonNull(entity, "entity")).getHandle().getEntityData();
        SynchedEntityData.DataItem<?>[] items = (SynchedEntityData.DataItem<?>[]) ITEMS.get(data);
        long fingerprint = OFFSET;
        for (SynchedEntityData.DataItem<?> item : items) {
            fingerprint = (fingerprint ^ item.getAccessor().id()) * PRIME;
            fingerprint = (fingerprint ^ valueHash(item.getValue())) * PRIME;
        }
        return fingerprint;
    }

    private static VarHandle items() {
        try {
            return MethodHandles.privateLookupIn(SynchedEntityData.class, MethodHandles.lookup())
                .findVarHandle(SynchedEntityData.class, "itemsById", SynchedEntityData.DataItem[].class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static int valueHash(Object value) {
        if (value instanceof ItemStack stack) {
            return 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount();
        }
        if (value instanceof byte[] bytes) {
            return Arrays.hashCode(bytes);
        }
        if (value instanceof int[] integers) {
            return Arrays.hashCode(integers);
        }
        if (value instanceof long[] longs) {
            return Arrays.hashCode(longs);
        }
        return Objects.hashCode(value);
    }
}
