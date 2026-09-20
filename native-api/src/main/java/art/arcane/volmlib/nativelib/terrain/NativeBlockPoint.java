package art.arcane.volmlib.nativelib.terrain;

public record NativeBlockPoint(int x, int y, int z) {
    public NativeBlockPoint offset(int dx, int dy, int dz) {
        return new NativeBlockPoint(x + dx, y + dy, z + dz);
    }

    public NativeBlockPoint above() {
        return offset(0, 1, 0);
    }

    public NativeBlockPoint below() {
        return offset(0, -1, 0);
    }
}
