package art.arcane.volmlib.util.decree.context;

import org.bukkit.World;

public abstract class WorldContextHandlerBase<S> {
    public Class<World> getType() {
        return World.class;
    }

    protected abstract boolean isPlayer(S sender);

    protected abstract World getWorld(S sender);

    public World handle(S sender) {
        return isPlayer(sender) ? getWorld(sender) : null;
    }
}
