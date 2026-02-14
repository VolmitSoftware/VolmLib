package art.arcane.volmlib.util.math;

import art.arcane.volmlib.util.collection.GBiset;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.Map;

public enum DirectionBasis {
    U(0, 1, 0),
    D(0, -1, 0),
    N(0, 0, -1),
    S(0, 0, 1),
    E(1, 0, 0),
    W(-1, 0, 0);

    private static KMap<GBiset<DirectionBasis, DirectionBasis>, DOP> permute = null;

    private final int x;
    private final int y;
    private final int z;

    DirectionBasis(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static DirectionBasis getDirection(BlockFace f) {
        return switch (f) {
            case DOWN -> D;
            case EAST, EAST_SOUTH_EAST, EAST_NORTH_EAST -> E;
            case NORTH, NORTH_WEST, NORTH_NORTH_WEST, NORTH_NORTH_EAST, NORTH_EAST -> N;
            case SELF, UP -> U;
            case SOUTH, SOUTH_WEST, SOUTH_SOUTH_WEST, SOUTH_SOUTH_EAST, SOUTH_EAST -> S;
            case WEST, WEST_SOUTH_WEST, WEST_NORTH_WEST -> W;
        };
    }

    public static DirectionBasis closest(Vector v) {
        double m = Double.MAX_VALUE;
        DirectionBasis s = null;

        for (DirectionBasis i : values()) {
            Vector x = i.toVector();
            double g = x.dot(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static DirectionBasis closest(Vector v, DirectionBasis... d) {
        double m = Double.MAX_VALUE;
        DirectionBasis s = null;

        for (DirectionBasis i : d) {
            Vector x = i.toVector();
            double g = x.distance(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static DirectionBasis closest(Vector v, KList<DirectionBasis> d) {
        double m = Double.MAX_VALUE;
        DirectionBasis s = null;

        for (DirectionBasis i : d) {
            Vector x = i.toVector();
            double g = x.distance(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static KList<DirectionBasis> news() {
        return new KList<DirectionBasis>().add(N, E, W, S);
    }

    public static DirectionBasis getDirection(Vector v) {
        Vector k = VectorMath.triNormalize(v.clone().normalize());

        for (DirectionBasis i : udnews()) {
            if (i.x == k.getBlockX() && i.y == k.getBlockY() && i.z == k.getBlockZ()) {
                return i;
            }
        }

        return DirectionBasis.N;
    }

    public static KList<DirectionBasis> udnews() {
        return new KList<DirectionBasis>().add(U, D, N, E, W, S);
    }

    public static DirectionBasis fromByte(byte b) {
        if (b > 5 || b < 0) {
            return null;
        }

        if (b == 0) {
            return D;
        } else if (b == 1) {
            return U;
        } else if (b == 2) {
            return N;
        } else if (b == 3) {
            return S;
        } else if (b == 4) {
            return W;
        } else {
            return E;
        }
    }

    public static void calculatePermutations() {
        if (permute != null) {
            return;
        }

        permute = new KMap<>();

        for (DirectionBasis i : udnews()) {
            for (DirectionBasis j : udnews()) {
                GBiset<DirectionBasis, DirectionBasis> b = new GBiset<>(i, j);

                if (i.equals(j)) {
                    permute.put(b, new DOP("DIRECT") {
                        @Override
                        public Vector op(Vector v) {
                            return v;
                        }
                    });
                } else if (i.reverse().equals(j)) {
                    if (i.isVertical()) {
                        permute.put(b, new DOP("R180CCZ") {
                            @Override
                            public Vector op(Vector v) {
                                return VectorMath.rotate90CCZ(VectorMath.rotate90CCZ(v));
                            }
                        });
                    } else {
                        permute.put(b, new DOP("R180CCY") {
                            @Override
                            public Vector op(Vector v) {
                                return VectorMath.rotate90CCY(VectorMath.rotate90CCY(v));
                            }
                        });
                    }
                } else if (getDirection(VectorMath.rotate90CX(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CX") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CX(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CCX(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CCX") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CCX(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CY(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CY") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CY(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CCY(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CCY") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CCY(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CZ(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CZ") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CZ(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CCZ(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CCZ") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CCZ(v);
                        }
                    });
                } else {
                    permute.put(b, new DOP("FAIL") {
                        @Override
                        public Vector op(Vector v) {
                            return v;
                        }
                    });
                }
            }
        }
    }

    @Override
    public String toString() {
        return switch (this) {
            case D -> "Down";
            case E -> "East";
            case N -> "North";
            case S -> "South";
            case U -> "Up";
            case W -> "West";
        };
    }

    public boolean isVertical() {
        return equals(D) || equals(U);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public boolean isCrooked(DirectionBasis to) {
        if (equals(to.reverse())) {
            return false;
        }

        return !equals(to);
    }

    public Vector angle(Vector initial, DirectionBasis d) {
        calculatePermutations();

        for (Map.Entry<GBiset<DirectionBasis, DirectionBasis>, DOP> entry : permute.entrySet()) {
            GBiset<DirectionBasis, DirectionBasis> i = entry.getKey();
            if (i.getA().equals(this) && i.getB().equals(d)) {
                return entry.getValue().op(initial);
            }
        }

        return initial;
    }

    public DirectionBasis reverse() {
        return switch (this) {
            case D -> U;
            case E -> W;
            case N -> S;
            case S -> N;
            case U -> D;
            case W -> E;
        };
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public byte byteValue() {
        return switch (this) {
            case D -> 0;
            case E -> 5;
            case N -> 2;
            case S -> 3;
            case U -> 1;
            case W -> 4;
        };
    }

    public BlockFace getFace() {
        return switch (this) {
            case D -> BlockFace.DOWN;
            case E -> BlockFace.EAST;
            case N -> BlockFace.NORTH;
            case S -> BlockFace.SOUTH;
            case U -> BlockFace.UP;
            case W -> BlockFace.WEST;
        };
    }

    public Axis getAxis() {
        return switch (this) {
            case D, U -> Axis.Y;
            case E, W -> Axis.X;
            case N, S -> Axis.Z;
        };
    }
}
