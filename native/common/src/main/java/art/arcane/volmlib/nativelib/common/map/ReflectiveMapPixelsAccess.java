package art.arcane.volmlib.nativelib.common.map;

import art.arcane.volmlib.nativelib.map.MapPixelsAccess;
import art.arcane.volmlib.nativelib.map.NativeMapSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.map.MapView;
import org.bukkit.map.MapRenderer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class ReflectiveMapPixelsAccess implements MapPixelsAccess {
    private static final int PIXEL_COUNT = 128 * 128;
    private static final AtomicBoolean CAPTURE_FAILURE_REPORTED = new AtomicBoolean();

    public Optional<NativeMapSnapshot> capture(MapView mapView) {
        if (mapView == null || !"CraftMapView".equals(mapView.getClass().getSimpleName())) {
            return Optional.empty();
        }
        try {
            Field worldMapField = accessibleField(mapView.getClass(), "worldMap");
            if (worldMapField == null) {
                reportCaptureFailure(mapView, "worldMap field is unavailable", null);
                return Optional.empty();
            }
            Object worldMap = worldMapField.get(mapView);
            if (worldMap == null) {
                reportCaptureFailure(mapView, "worldMap is null", null);
                return Optional.empty();
            }
            Field colorsField = accessibleField(worldMap.getClass(), "colors");
            if (colorsField == null || colorsField.getType() != byte[].class) {
                reportCaptureFailure(mapView, "map colors field is unavailable", null);
                return Optional.empty();
            }
            synchronized (worldMap) {
                if (!hasVanillaRenderer(mapView)) {
                    return Optional.empty();
                }
                Object rawColors = colorsField.get(worldMap);
                if (!(rawColors instanceof byte[] colors) || colors.length != PIXEL_COUNT) {
                    reportCaptureFailure(mapView, "map colors have an invalid shape", null);
                    return Optional.empty();
                }
                MapView.Scale mapScale = mapView.getScale();
                if (mapScale == null) {
                    return Optional.empty();
                }
                return Optional.of(new NativeMapSnapshot(
                    mapView.getId(), mapScale.getValue(), mapView.isTrackingPosition(), mapView.isLocked(), colors));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            reportCaptureFailure(mapView, "map data reflection failed", error);
            return Optional.empty();
        }
    }

    private static boolean hasVanillaRenderer(MapView mapView) {
        List<MapRenderer> renderers;
        try {
            renderers = mapView.getRenderers();
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
        return renderers != null
            && renderers.size() == 1
            && renderers.get(0) != null
            && "CraftMapRenderer".equals(renderers.get(0).getClass().getSimpleName());
    }

    private static Field accessibleField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers()) || !field.trySetAccessible()) {
                    return null;
                }
                return field;
            } catch (NoSuchFieldException error) {
                current = current.getSuperclass();
            } catch (RuntimeException | LinkageError error) {
                return null;
            }
        }
        return null;
    }

    private static void reportCaptureFailure(MapView mapView, String reason, Throwable error) {
        if (!CAPTURE_FAILURE_REPORTED.compareAndSet(false, true)) {
            return;
        }
        Throwable cause = error == null ? new IllegalStateException(reason) : error;
        Bukkit.getLogger().log(Level.WARNING,
            "Native vanilla map capture failed for " + mapView.getClass().getName()
                + "; map pixels are unavailable", cause);
    }
}
