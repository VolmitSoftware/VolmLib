package art.arcane.volmlib.nativelib.map;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.map.MapView;
import java.util.Optional;

@NativeBinding("map.NativeMapPixelsAccess")
public interface MapPixelsAccess {
    Optional<NativeMapSnapshot> capture(MapView mapView);
}
