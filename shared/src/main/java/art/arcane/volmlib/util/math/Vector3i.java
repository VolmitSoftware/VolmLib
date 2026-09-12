package art.arcane.volmlib.util.math;

public class Vector3i implements Cloneable {
    private final int x;
    private final int y;
    private final int z;

    public Vector3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getBlockX() {
        return x;
    }

    public int getBlockY() {
        return y;
    }

    public int getBlockZ() {
        return z;
    }

    @Override
    public Vector3i clone() {
        try {
            return (Vector3i) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vector3i other)) {
            return false;
        }

        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return x ^ (z << 12) ^ (y << 24);
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
