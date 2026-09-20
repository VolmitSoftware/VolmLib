package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class NativeEntityLoot {
    private static volatile Predicate<NativeEntityLoot> replacement;
    private final Entity entity;

    private NativeEntityLoot(Entity entity) {
        this.entity = entity;
    }

    public static NativeEntityLoot of(NativeSpawnedEntity entity) {
        return entity == null ? null : new NativeEntityLoot(entity.entity());
    }

    public static void bind(Predicate<NativeEntityLoot> handler) {
        replacement = Objects.requireNonNull(handler);
    }

    public static boolean replace(LivingEntity entity) {
        Predicate<NativeEntityLoot> handler = replacement;
        return entity != null && entity.level() instanceof ServerLevel
                && handler != null && handler.test(new NativeEntityLoot(entity));
    }

    public boolean mob() {
        return entity instanceof Mob;
    }

    public boolean container() {
        return entity instanceof ContainerEntity && entity.level() instanceof ServerLevel;
    }

    public NativeWorld world() {
        return entity.level() instanceof ServerLevel level ? new ModdedPlatformWorld(level) : null;
    }

    public String type() {
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public void tag(String value) {
        entity.addTag(value);
    }

    public Set<String> tags() {
        return entity.entityTags();
    }

    public void prepareContainer() {
        ContainerEntity container = (ContainerEntity) entity;
        container.setContainerLootTable(null);
        container.clearItemStacks();
    }

    public void fill(List<NativeItemStack> items, RNG random, Consumer<String> debug) {
        List<ItemStack> stacks = new ArrayList<>(items.size());
        NativeItemStack.appendTo(stacks, items);
        NativeContainerLoot.fillContainer((ContainerEntity) entity, stacks, random, debug);
    }

    public void emit(List<NativeItemStack> drops) {
        LivingEntity living = (LivingEntity) entity;
        ServerLevel level = (ServerLevel) entity.level();
        for (NativeItemStack stack : drops) {
            if (stack != null && !stack.isEmpty()) {
                living.spawnAtLocation(level, stack.stack());
            }
        }
    }
}
