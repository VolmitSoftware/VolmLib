package art.arcane.volmlib.util.data;

public class Dimension {
    private final int width;
    private final int height;
    private final int depth;

    public Dimension(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public Dimension(int width, int height) {
        this.width = width;
        this.height = height;
        this.depth = 0;
    }

    public DimensionFace getPane() {
        if (width == 1) {
            return DimensionFace.X;
        }

        if (height == 1) {
            return DimensionFace.Y;
        }

        if (depth == 1) {
            return DimensionFace.Z;
        }

        return null;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }
}
