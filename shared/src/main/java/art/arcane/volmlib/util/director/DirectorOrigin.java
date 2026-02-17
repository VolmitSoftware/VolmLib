package art.arcane.volmlib.util.director;

public enum DirectorOrigin {
    PLAYER,
    CONSOLE,
    BOTH;

    public boolean validFor(boolean isPlayer) {
        if (isPlayer) {
            return this == PLAYER || this == BOTH;
        }

        return this == CONSOLE || this == BOTH;
    }
}
