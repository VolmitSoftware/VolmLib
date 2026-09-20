package art.arcane.volmlib.nativelib.minecraft26_2.modded;

public final class NativeServerLogMessages {
    private NativeServerLogMessages() {
    }

    public static Kind classify(String loggerName, String message) {
        if (loggerName == null || !loggerName.startsWith("net.minecraft") || message == null) {
            return Kind.OTHER;
        }
        if (message.contains("Ignoring heightmap data for chunk")) {
            return Kind.IGNORED_HEIGHTMAP;
        }
        if (message.contains("Could not save data net.minecraft.world.entity.raid.PersistentRaid")) {
            return Kind.RAID_PERSISTENCE;
        }
        if (message.contains("UUID of added entity already exists")) {
            return Kind.DUPLICATE_ENTITY;
        }
        return Kind.OTHER;
    }

    public enum Kind {
        IGNORED_HEIGHTMAP,
        RAID_PERSISTENCE,
        DUPLICATE_ENTITY,
        OTHER
    }
}
