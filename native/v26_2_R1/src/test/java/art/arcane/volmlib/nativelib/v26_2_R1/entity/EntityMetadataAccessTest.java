package art.arcane.volmlib.nativelib.v26_2_R1.entity;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.entity.EntityMetadataAccess;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityMetadataAccessTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.APPLE.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
    }

    @Test
    void handUseBabyAndAggressiveTransitionsChangeWithoutConsumingTrackerDirtiness() {
        Fixture fixture = fixture();
        long idle = fixture.access().fingerprint(fixture.entity());
        fixture.data().set(Holder.HAND_USE, (byte) 1);
        long using = fixture.access().fingerprint(fixture.entity());
        assertNotEquals(idle, using);
        fixture.data().packDirty();
        assertEquals(using, fixture.access().fingerprint(fixture.entity()));
        fixture.data().set(Holder.HAND_USE, (byte) 3);
        long offhand = fixture.access().fingerprint(fixture.entity());
        assertNotEquals(using, offhand);
        fixture.data().set(Holder.HAND_USE, (byte) 0);
        assertEquals(idle, fixture.access().fingerprint(fixture.entity()));
        fixture.data().set(Holder.BABY, true);
        long baby = fixture.access().fingerprint(fixture.entity());
        assertNotEquals(idle, baby);
        fixture.data().set(Holder.MOB_FLAGS, (byte) 4);
        long aggressive = fixture.access().fingerprint(fixture.entity());
        assertNotEquals(baby, aggressive);
        fixture.data().set(Holder.MOB_FLAGS, (byte) 4);
        fixture.data().packDirty();
        assertEquals(aggressive, fixture.access().fingerprint(fixture.entity()));
    }

    @Test
    void mutableItemMetadataUsesContentRatherThanObjectIdentity() {
        Fixture fixture = fixture();
        long original = fixture.access().fingerprint(fixture.entity());
        fixture.data().get(Holder.ITEM).setCount(2);
        assertNotEquals(original, fixture.access().fingerprint(fixture.entity()));
        fixture.data().get(Holder.ITEM).setCount(1);
        assertEquals(original, fixture.access().fingerprint(fixture.entity()));
    }

    private static Fixture fixture() {
        SynchedEntityData data = new SynchedEntityData.Builder(new Holder())
            .define(Holder.HAND_USE, (byte) 0).define(Holder.BABY, false)
            .define(Holder.MOB_FLAGS, (byte) 0).define(Holder.ITEM, new ItemStack(Items.APPLE)).build();
        Entity handle = mock(Entity.class);
        CraftEntity entity = mock(CraftEntity.class);
        when(entity.getHandle()).thenReturn(handle);
        when(handle.getEntityData()).thenReturn(data);
        return new Fixture(entity, data, NativeAdapters.require(EntityMetadataAccess.class, "26.2"));
    }

    private record Fixture(CraftEntity entity, SynchedEntityData data, EntityMetadataAccess access) {
    }

    private static final class Holder implements SyncedDataHolder {
        private static final EntityDataAccessor<Byte> HAND_USE = SynchedEntityData.defineId(Holder.class, EntityDataSerializers.BYTE);
        private static final EntityDataAccessor<Boolean> BABY = SynchedEntityData.defineId(Holder.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Byte> MOB_FLAGS = SynchedEntityData.defineId(Holder.class, EntityDataSerializers.BYTE);
        private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(Holder.class, EntityDataSerializers.ITEM_STACK);

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        }

        @Override
        public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> values) {
        }
    }
}
