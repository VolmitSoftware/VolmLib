package art.arcane.volmlib.nativelib.monitor;

import org.bukkit.entity.FallingBlock;

@FunctionalInterface
public interface FallingBlockTickHook {
    TickDecision decide(FallingBlock entity);
}
