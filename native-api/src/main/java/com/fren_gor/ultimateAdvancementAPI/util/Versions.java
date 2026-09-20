package com.fren_gor.ultimateAdvancementAPI.util;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.NativeVersion;
import art.arcane.volmlib.nativelib.advancement.AdvancementAccess;
import com.fren_gor.ultimateAdvancementAPI.nms.util.ReflectionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Versions {
    private Versions() {
    }

    public static Optional<String> getNMSVersion() {
        return NativeVersion.resolve(ReflectionUtil.MINECRAFT_VERSION)
                .filter(version -> NativeAdapters.available(AdvancementAccess.class, version))
                .map(NativeVersion::packageName);
    }

    public static String getApiVersion() {
        return "2.7.2";
    }

    public static List<String> getSupportedVersions() {
        List<String> versions = new ArrayList<>();
        for (NativeVersion version : NativeVersion.values()) {
            if (NativeAdapters.available(AdvancementAccess.class, version)) {
                versions.addAll(version.minecraftVersions());
            }
        }
        return List.copyOf(versions);
    }

    public static List<String> getSupportedNMSVersions() {
        List<String> versions = new ArrayList<>();
        for (NativeVersion version : NativeVersion.values()) {
            if (NativeAdapters.available(AdvancementAccess.class, version)) {
                versions.add(version.packageName());
            }
        }
        return List.copyOf(versions);
    }

    public static String getNMSVersionsRange() {
        return getNMSVersion().map(Versions::getNMSVersionsRange).orElse(null);
    }

    public static String getNMSVersionsRange(String version) {
        List<String> releases = getNMSVersionsList(version);
        return releases == null ? null : String.join(", ", releases);
    }

    public static List<String> getNMSVersionsList() {
        return getNMSVersion().map(Versions::getNMSVersionsList).orElse(null);
    }

    public static List<String> getNMSVersionsList(String version) {
        for (NativeVersion candidate : NativeVersion.values()) {
            if (candidate.packageName().equals(version)) {
                return candidate.minecraftVersions();
            }
        }
        return null;
    }

    public static String removeInitialV(String string) {
        return string == null || string.isEmpty() || string.charAt(0) != 'v' ? string : string.substring(1);
    }
}
