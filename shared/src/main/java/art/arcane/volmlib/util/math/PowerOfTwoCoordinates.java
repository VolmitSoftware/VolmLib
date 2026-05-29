package art.arcane.volmlib.util.math;

public final class PowerOfTwoCoordinates {
    public static final int CHUNK_BITS = 4;
    public static final int REGION_BITS = 9;
    public static final int REGION_CHUNK_BITS = 5;
    public static final int CHUNK_MASK = 15;
    public static final int REGION_CHUNK_MASK = 31;

    private PowerOfTwoCoordinates() {
    }

    public static int floorDivPow2(int value, int bits) {
        return value >> bits;
    }

    public static int ceilDivPow2(int value, int bits) {
        return (int) (-((-(long) value) >> bits));
    }

    public static int localMaskPow2(int value, int bits) {
        return value & ((1 << bits) - 1);
    }

    public static int packLocalPow2(int x, int z, int bits) {
        return (x << bits) | localMaskPow2(z, bits);
    }

    public static int unpackLocalXPow2(int packed, int bits) {
        return packed >> bits;
    }

    public static int unpackLocalZPow2(int packed, int bits) {
        return localMaskPow2(packed, bits);
    }

    public static int blockToChunkFloor(int block) {
        return floorDivPow2(block, CHUNK_BITS);
    }

    public static int blockToRegionFloor(int block) {
        return floorDivPow2(block, REGION_BITS);
    }

    public static int chunkToBlock(int chunk) {
        return chunk << CHUNK_BITS;
    }

    public static int chunkToRegion(int chunk) {
        return floorDivPow2(chunk, REGION_CHUNK_BITS);
    }

    public static int regionToChunk(int region) {
        return region << REGION_CHUNK_BITS;
    }

    public static int regionToBlock(int region) {
        return region << REGION_BITS;
    }

    public static int packLocal16(int x, int z) {
        return packLocalPow2(x, z, CHUNK_BITS);
    }

    public static int unpackLocal16X(int packed) {
        return unpackLocalXPow2(packed, CHUNK_BITS);
    }

    public static int unpackLocal16Z(int packed) {
        return unpackLocalZPow2(packed, CHUNK_BITS);
    }

    public static int packLocal32(int x, int z) {
        return packLocalPow2(x, z, REGION_CHUNK_BITS);
    }

    public static int unpackLocal32X(int packed) {
        return unpackLocalXPow2(packed, REGION_CHUNK_BITS);
    }

    public static int unpackLocal32Z(int packed) {
        return unpackLocalZPow2(packed, REGION_CHUNK_BITS);
    }
}
