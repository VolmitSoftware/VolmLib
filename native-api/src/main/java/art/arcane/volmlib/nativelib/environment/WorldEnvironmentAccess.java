package art.arcane.volmlib.nativelib.environment;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.World;

@NativeBinding("environment.NativeWorldEnvironmentAccess")
public interface WorldEnvironmentAccess {
    WorldEnvironment sample(World world, Position position);

    record Position(double x, double y, double z) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Environment sample position must be finite");
            }
        }
    }
}
