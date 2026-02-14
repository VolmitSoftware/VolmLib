package art.arcane.volmlib.util.math;

import org.bukkit.util.Vector;

public class Position2 {
    private int x;
    private int z;

    public Position2(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public Position2(Vector center) {
        this.x = center.getBlockX();
        this.z = center.getBlockZ();
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    @Override
    public String toString() {
        return "[" + x + "," + z + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + z;
        return result;
    }

    public Position2 regionToChunk() {
        return new Position2(x << 5, z << 5);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Position2 other)) {
            return false;
        }
        return x == other.x && z == other.z;
    }

    public double distance(Position2 center) {
        return Math.pow(center.getX() - x, 2) + Math.pow(center.getZ() - z, 2);
    }

    public Position2 add(int x, int z) {
        return new Position2(this.x + x, this.z + z);
    }

    public Position2 blockToChunk() {
        return new Position2(x >> 4, z >> 4);
    }
}
