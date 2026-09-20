package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.entity.NativeEntityOptions;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Objects;
import java.util.function.DoubleSupplier;

public final class NativeSpawnedEntity {
    private final Entity entity;

    public NativeSpawnedEntity(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    public Entity entity() {
        return entity;
    }

    public boolean living() {
        return entity instanceof LivingEntity;
    }

    public boolean mob() {
        return entity instanceof Mob;
    }

    public boolean alive() {
        return entity.isAlive();
    }

    public boolean inWorld(NativeEntityRuntime runtime) {
        return entity.level() == runtime.level();
    }

    public double x() {
        return entity.getX();
    }

    public double y() {
        return entity.getY();
    }

    public double z() {
        return entity.getZ();
    }

    public int blockX() {
        return entity.blockPosition().getX();
    }

    public int blockY() {
        return entity.blockPosition().getY();
    }

    public int blockZ() {
        return entity.blockPosition().getZ();
    }

    public double eyeY() {
        return ((LivingEntity) entity).getEyeY();
    }

    public void position(double x, double y, double z) {
        entity.setPos(x, y, z);
    }

    public void applyBase(NativeEntityOptions options, String displayName) {
        if (displayName != null) {
            entity.setCustomName(Component.literal(displayName));
        }
        entity.setCustomNameVisible(options.isCustomNameVisible());
        entity.setGlowingTag(options.isGlowing());
        entity.setNoGravity(!options.isGravity());
        entity.setInvulnerable(options.isInvulnerable());
        entity.setSilent(options.isSilent());
    }

    public boolean persistence(boolean persistent) {
        NativeEntityBehavior.configurePersistence(entity, persistent);
        return !persistent || (entity.getType().canSerialize() && entity.shouldBeSaved());
    }

    public String typeKey() {
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public void ride(NativeSpawnedEntity vehicle) {
        entity.startRiding(vehicle.entity);
    }

    public void configureMob(NativeEntityOptions options) {
        Mob mob = (Mob) entity;
        mob.setNoAi(NativeEntityRuntime.shouldDisableAi(options.isAi()));
        NativeEntityBehavior.configureAwareness(mob, options.isAware());
        mob.setCanPickUpLoot(options.isPickupItems());
        if (!options.isRemovable()) {
            mob.setPersistenceRequired();
        }
    }

    public void leashTo(NativeSpawnedEntity holder) {
        ((Mob) entity).setLeashedTo(holder.entity, true);
    }

    public boolean applyAttribute(NativeEntityRuntime runtime, String attribute, String modifierId, String operation, DoubleSupplier amount) {
        LivingEntity living = (LivingEntity) entity;
        Registry<Attribute> registry = runtime.level().registryAccess().lookupOrThrow(Registries.ATTRIBUTE);
        Holder<Attribute> holder = NativeItemTranslator.resolveAttribute(registry, attribute);
        if (holder == null) {
            return false;
        }
        AttributeInstance instance = living.getAttributes().getInstance(holder);
        if (instance == null) {
            return true;
        }
        Identifier id = Identifier.tryParse(modifierId);
        if (id != null) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount.getAsDouble(),
                    NativeItemTranslator.operationFor(operation)));
        }
        return true;
    }

    public void equip(Equipment equipment, NativeItemStack nativeStack) {
        ItemStack stack = nativeStack == null ? null : nativeStack.stack();
        if (stack != null && !stack.isEmpty()) {
            ((LivingEntity) entity).setItemSlot(switch (equipment) {
                case HEAD -> EquipmentSlot.HEAD;
                case CHEST -> EquipmentSlot.CHEST;
                case LEGS -> EquipmentSlot.LEGS;
                case FEET -> EquipmentSlot.FEET;
                case MAINHAND -> EquipmentSlot.MAINHAND;
                case OFFHAND -> EquipmentSlot.OFFHAND;
            }, stack);
        }
    }

    public void configureAnimal(NativeEntityOptions options) {
        if (options.isBaby() && entity instanceof Mob mob) {
            mob.setBaby(true);
        }
        if (entity instanceof Panda panda) {
            panda.setMainGene(gene(options.getPandaMainGene()));
            panda.setHiddenGene(gene(options.getPandaHiddenGene()));
        }
        if (entity instanceof Villager villager) {
            NativeEntityBehavior.configurePersistence(villager, true);
        }
    }

    public MotionState pausePhysics(int invulnerableTicks) {
        MotionState original = new MotionState(entity.isInvulnerable(), entity.noPhysics,
                entity.invulnerableTime, entity instanceof Mob mob && mob.isNoAi());
        entity.setInvulnerable(true);
        entity.noPhysics = true;
        entity.invulnerableTime = invulnerableTicks;
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        return original;
    }

    public void restoreMotion(MotionState state) {
        entity.invulnerableTime = state.invulnerableTicks();
        entity.noPhysics = state.noPhysics();
        entity.setInvulnerable(state.invulnerable());
        if (entity instanceof Mob mob) {
            mob.setNoAi(state.noAi());
        }
    }

    private static Panda.Gene gene(String value) {
        if (value == null || value.isBlank()) {
            return Panda.Gene.NORMAL;
        }
        try {
            return Panda.Gene.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Panda.Gene.NORMAL;
        }
    }

    public record MotionState(boolean invulnerable, boolean noPhysics, int invulnerableTicks, boolean noAi) {
    }

    public enum Equipment {
        HEAD, CHEST, LEGS, FEET, MAINHAND, OFFHAND
    }
}
