package com.fren_gor.ultimateAdvancementAPI.nms.util;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.advancement.AdvancementAccess;
import org.bukkit.Bukkit;
import java.util.Objects;
import java.util.logging.Level;

public final class ReflectionUtil {
    public static final String MINECRAFT_VERSION = Bukkit.getBukkitVersion().split("\\.build\\.|-")[0];
    private static final String[] VERSION_COMPONENTS = MINECRAFT_VERSION.split("\\.");
    private static final int VERSION_OFFSET = "1".equals(VERSION_COMPONENTS[0]) ? 1 : 0;
    public static final int VERSION = Integer.parseInt(VERSION_COMPONENTS[VERSION_OFFSET]);
    public static final int MINOR_VERSION = VERSION_COMPONENTS.length > VERSION_OFFSET + 1
            ? Integer.parseInt(VERSION_COMPONENTS[VERSION_OFFSET + 1]) : 0;

    private ReflectionUtil() {
    }

    public static boolean classExists(String className) {
        Objects.requireNonNull(className, "className");
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    public static Class<?> getNMSClass(String name, String minecraftPackage) {
        try {
            return NativeAdapters.require(AdvancementAccess.class).minecraftClass(name, minecraftPackage);
        } catch (ClassNotFoundException failure) {
            Bukkit.getLogger().log(Level.SEVERE, "Unable to resolve advancement class " + name, failure);
            return null;
        }
    }

    public static Class<?> getCBClass(String name) {
        try {
            return NativeAdapters.require(AdvancementAccess.class).craftClass(name);
        } catch (ClassNotFoundException failure) {
            Bukkit.getLogger().log(Level.SEVERE, "Unable to resolve advancement server class " + name, failure);
            return null;
        }
    }

    public static <T> Class<? extends T> getWrapperClass(Class<T> wrapper) {
        return NativeAdapters.require(AdvancementAccess.class).wrapperClass(wrapper);
    }
}
