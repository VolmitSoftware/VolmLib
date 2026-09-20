package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.modded.EntityBehaviorTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Set;

public final class NativeEntityBehavior {
    private static final System.Logger LOGGER = System.getLogger(NativeEntityBehavior.class.getName());
    private static volatile EntityBehaviorTags tags;

    private NativeEntityBehavior() {
    }

    public static void bind(EntityBehaviorTags configuredTags) {
        tags = Objects.requireNonNull(configuredTags, "configuredTags");
    }

    public static void configurePersistence(Entity entity, boolean persistent) {
        String tag = Objects.requireNonNull(tags, "Entity behavior tags have not been bound").nonPersistent();
        if (persistent) {
            entity.removeTag(tag);
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            return;
        }
        if (!entity.entityTags().contains(tag) && !entity.addTag(tag)) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not mark entity '" + entity.getStringUUID() + "' as non-persistent");
        }
    }

    public static boolean shouldSave(Entity entity, boolean vanillaResult) {
        return shouldSave(entity.entityTags(), vanillaResult);
    }

    public static boolean shouldSave(Set<String> entityTags, boolean vanillaResult) {
        EntityBehaviorTags configured = tags;
        return vanillaResult && (configured == null || !entityTags.contains(configured.nonPersistent()));
    }

    public static void configurePersistenceTags(Set<String> entityTags, boolean persistent) {
        String tag = Objects.requireNonNull(tags, "Entity behavior tags have not been bound").nonPersistent();
        if (persistent) {
            entityTags.remove(tag);
        } else {
            entityTags.add(tag);
        }
    }

    public static void configureAwareness(Mob mob, boolean aware) {
        String tag = Objects.requireNonNull(tags, "Entity behavior tags have not been bound").unaware();
        if (aware) {
            mob.removeTag(tag);
            return;
        }
        if (!mob.entityTags().contains(tag) && !mob.addTag(tag)) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not mark mob '" + mob.getStringUUID() + "' as unaware");
        }
    }

    public static boolean isAware(Mob mob) {
        return isAware(mob.entityTags());
    }

    public static boolean isAware(Set<String> entityTags) {
        EntityBehaviorTags configured = tags;
        return configured == null || !entityTags.contains(configured.unaware());
    }

    public static void configureAwarenessTags(Set<String> entityTags, boolean aware) {
        String tag = Objects.requireNonNull(tags, "Entity behavior tags have not been bound").unaware();
        if (aware) {
            entityTags.remove(tag);
        } else {
            entityTags.add(tag);
        }
    }
}
