package art.arcane.volmlib.nativelib.map;

public record NativeMapSnapshot(int sourceMapId, byte scale, boolean tracking, boolean locked, byte[] pixels) {
    public NativeMapSnapshot {
        if (scale < 0 || scale > 4 || pixels == null || pixels.length != 128 * 128) {
            throw new IllegalArgumentException("Invalid native map snapshot");
        }
        pixels = pixels.clone();
    }

    @Override
    public byte[] pixels() {
        return pixels.clone();
    }
}
