package art.arcane.volmlib.nativelib.terrain;

import org.bukkit.World;

import java.util.OptionalLong;

public interface NativeWorldClock {
    String description();
    boolean hasMutableClock(World world) throws ReflectiveOperationException;
    OptionalLong readDayTime(World world) throws ReflectiveOperationException;
    boolean writeDayTime(World world, long dayTime) throws ReflectiveOperationException;
    void syncTime(World world) throws ReflectiveOperationException;
}
