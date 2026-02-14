package art.arcane.volmlib.util.math;

import art.arcane.volmlib.util.collection.GListAdapter;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public class VectorMath {
    public static Vector scaleStatic(Axis x, Vector v, double amt) {
        return switch (x) {
            case X -> scaleX(v, amt);
            case Y -> scaleY(v, amt);
            case Z -> scaleZ(v, amt);
        };
    }

    public static Vector scaleX(Vector v, double amt) {
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();
        double rx = x == 0 ? 1 : amt / x;

        return new Vector(x * rx, y * rx, z * rx);
    }

    public static Vector scaleY(Vector v, double amt) {
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();
        double rx = y == 0 ? 1 : amt / y;

        return new Vector(x * rx, y * rx, z * rx);
    }

    public static Vector scaleZ(Vector v, double amt) {
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();
        double rx = z == 0 ? 1 : amt / z;

        return new Vector(x * rx, y * rx, z * rx);
    }

    public static Vector reverseXZ(Vector v) {
        v.setX(-v.getX());
        v.setZ(-v.getZ());
        return v;
    }

    public static boolean isLookingNear(Location a, Location b, double maxOff) {
        Vector perfect = VectorMath.direction(a, b);
        Vector actual = a.getDirection();

        return perfect.distance(actual) <= maxOff;
    }

    public static Vector rotate90CX(Vector v) {
        return new Vector(v.getX(), -v.getZ(), v.getY());
    }

    public static Vector rotate90CCX(Vector v) {
        return new Vector(v.getX(), v.getZ(), -v.getY());
    }

    public static Vector rotate90CY(Vector v) {
        return new Vector(-v.getZ(), v.getY(), v.getX());
    }

    public static Vector rotate90CCY(Vector v) {
        return new Vector(v.getZ(), v.getY(), -v.getX());
    }

    public static Vector rotate90CZ(Vector v) {
        return new Vector(v.getY(), -v.getX(), v.getZ());
    }

    public static Vector rotate90CCZ(Vector v) {
        return new Vector(-v.getY(), v.getX(), v.getZ());
    }

    public static Vector rotate90CX(Vector v, int s) {
        return new Vector(v.getX(), -v.getZ() + s, v.getY());
    }

    public static Vector rotate90CCX(Vector v, int s) {
        return new Vector(v.getX(), v.getZ(), -v.getY() + s);
    }

    public static Vector rotate90CY(Vector v, int s) {
        return new Vector(-v.getZ() + s, v.getY(), v.getX());
    }

    public static Vector rotate90CCY(Vector v, int s) {
        return new Vector(v.getZ(), v.getY(), -v.getX() + s);
    }

    public static Vector rotate90CZ(Vector v, int s) {
        return new Vector(v.getY(), -v.getX() + s, v.getZ());
    }

    public static Vector rotate90CCZ(Vector v, int s) {
        return new Vector(-v.getY() + s, v.getX(), v.getZ());
    }

    private static double round(double value, int precision) {
        double scale = Math.pow(10, precision);
        return Math.round(value * scale) / scale;
    }

    public static Vector clip(Vector v, int decimals) {
        v.setX(round(v.getX(), decimals));
        v.setY(round(v.getY(), decimals));
        v.setZ(round(v.getZ(), decimals));
        return v;
    }

    public static Vector rotateVectorCC(Vector vec, Vector axis, double deg) {
        double theta = Math.toRadians(deg);
        double x, y, z;
        double u, vv, w;
        x = vec.getX();
        y = vec.getY();
        z = vec.getZ();
        u = axis.getX();
        vv = axis.getY();
        w = axis.getZ();
        double f = u * x + vv * y + w * z;
        double xPrime = u * f * (1d - Math.cos(theta)) + x * Math.cos(theta) + (-w * y + vv * z) * Math.sin(theta);
        double yPrime = vv * f * (1d - Math.cos(theta)) + y * Math.cos(theta) + (w * x - u * z) * Math.sin(theta);
        double zPrime = w * f * (1d - Math.cos(theta)) + z * Math.cos(theta) + (-vv * x + u * y) * Math.sin(theta);

        return clip(new Vector(xPrime, yPrime, zPrime), 4);
    }

    public static KList<BlockFace> split(BlockFace f) {
        KList<BlockFace> faces = new KList<>();

        switch (f) {
            case DOWN:
                faces.add(BlockFace.DOWN);
                break;
            case EAST:
                faces.add(BlockFace.EAST);
                break;
            case EAST_NORTH_EAST:
                faces.add(BlockFace.EAST);
                faces.add(BlockFace.EAST);
                faces.add(BlockFace.NORTH);
                break;
            case EAST_SOUTH_EAST:
                faces.add(BlockFace.EAST);
                faces.add(BlockFace.EAST);
                faces.add(BlockFace.SOUTH);
                break;
            case NORTH:
                faces.add(BlockFace.NORTH);
                break;
            case NORTH_EAST:
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.EAST);
                break;
            case NORTH_NORTH_EAST:
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.EAST);
                break;
            case NORTH_NORTH_WEST:
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.WEST);
                break;
            case NORTH_WEST:
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.WEST);
                break;
            case SELF:
                faces.add(BlockFace.SELF);
                break;
            case SOUTH:
                faces.add(BlockFace.SOUTH);
                break;
            case SOUTH_EAST:
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.EAST);
                break;
            case SOUTH_SOUTH_EAST:
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.EAST);
                break;
            case SOUTH_SOUTH_WEST:
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.WEST);
                break;
            case SOUTH_WEST:
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.WEST);
                break;
            case UP:
                faces.add(BlockFace.UP);
                break;
            case WEST:
                faces.add(BlockFace.WEST);
                break;
            case WEST_NORTH_WEST:
                faces.add(BlockFace.WEST);
                faces.add(BlockFace.WEST);
                faces.add(BlockFace.NORTH);
                break;
            case WEST_SOUTH_WEST:
                faces.add(BlockFace.WEST);
                faces.add(BlockFace.WEST);
                faces.add(BlockFace.SOUTH);
                break;
            default:
                break;
        }

        return faces;
    }

    public static Vector direction(Location from, Location to) {
        return to.clone().subtract(from.clone()).toVector().normalize();
    }

    public static Vector directionNoNormal(Location from, Location to) {
        return to.clone().subtract(from.clone()).toVector();
    }

    public static Vector toVector(float yaw, float pitch) {
        return new Vector(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(-yaw));
    }

    public static void impulse(Entity e, Vector v) {
        impulse(e, v, 1.0);
    }

    public static void impulse(Entity e, Vector v, double effectiveness) {
        Vector vx = e.getVelocity();
        vx.add(v.clone().multiply(effectiveness));
        e.setVelocity(vx);
    }

    public static Vector reverse(Vector v) {
        if (v.getX() != 0) {
            v.setX(-v.getX());
        }

        if (v.getY() != 0) {
            v.setY(-v.getY());
        }

        if (v.getZ() != 0) {
            v.setZ(-v.getZ());
        }

        return v;
    }

    public static double getSpeed(Vector v) {
        Vector vi = new Vector(0, 0, 0);
        Vector vt = new Vector(0, 0, 0).add(v);

        return vi.distance(vt);
    }

    public static KList<Vector> shift(Vector vector, KList<Vector> vectors) {
        return new KList<>(new GListAdapter<Vector, Vector>() {
            @Override
            public Vector onAdapt(Vector from) {
                return from.add(vector);
            }
        }.adapt(vectors));
    }

    public static BlockFace getBlockFace(Vector v) {
        Vector p = triNormalize(v);

        for (BlockFace i : BlockFace.values()) {
            if (p.getX() == i.getModX() && p.getY() == i.getModY() && p.getZ() == i.getModZ()) {
                return i;
            }
        }

        for (BlockFace i : BlockFace.values()) {
            if (p.getX() == i.getModX() && p.getZ() == i.getModZ()) {
                return i;
            }
        }

        for (BlockFace i : BlockFace.values()) {
            if (p.getY() == i.getModY() && p.getZ() == i.getModZ()) {
                return i;
            }
        }

        for (BlockFace i : BlockFace.values()) {
            if (p.getX() == i.getModX() || p.getY() == i.getModY()) {
                return i;
            }
        }

        for (BlockFace i : BlockFace.values()) {
            if (p.getX() == i.getModX() || p.getY() == i.getModY() || p.getZ() == i.getModZ()) {
                return i;
            }
        }

        return null;
    }

    public static Vector angleLeft(Vector v, float amt) {
        Location l = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
        l.setDirection(v);
        float y = l.getYaw();
        float p = l.getPitch();
        CDou cy = new CDou(360);
        CDou cp = new CDou(180);
        cy.set(y);
        cp.set(p);
        cy.sub(amt);
        l.setYaw((float) cy.get());
        l.setPitch((float) cp.get());

        return l.getDirection();
    }

    public static Vector angleRight(Vector v, float amt) {
        Location l = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
        l.setDirection(v);
        float y = l.getYaw();
        float p = l.getPitch();
        CDou cy = new CDou(360);
        CDou cp = new CDou(180);
        cy.set(y);
        cp.set(p);
        cy.add(amt);
        l.setYaw((float) cy.get());
        l.setPitch((float) cp.get());

        return l.getDirection();
    }

    public static Vector angleUp(Vector v, float amt) {
        Location l = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
        l.setDirection(v);
        float y = l.getYaw();
        float p = l.getPitch();
        CDou cy = new CDou(360);
        cy.set(y);
        l.setYaw((float) cy.get());
        l.setPitch(Math.max(-90, p - amt));

        return l.getDirection();
    }

    public static Vector angleDown(Vector v, float amt) {
        Location l = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
        l.setDirection(v);
        float y = l.getYaw();
        float p = l.getPitch();
        CDou cy = new CDou(360);
        cy.set(y);
        l.setYaw((float) cy.get());
        l.setPitch(Math.min(90, p + amt));

        return l.getDirection();
    }

    public static Vector triNormalize(Vector direction) {
        Vector v = direction.clone();
        v.normalize();

        if (v.getX() > 0.333) {
            v.setX(1);
        } else if (v.getX() < -0.333) {
            v.setX(-1);
        } else {
            v.setX(0);
        }

        if (v.getY() > 0.333) {
            v.setY(1);
        } else if (v.getY() < -0.333) {
            v.setY(-1);
        } else {
            v.setY(0);
        }

        if (v.getZ() > 0.333) {
            v.setZ(1);
        } else if (v.getZ() < -0.333) {
            v.setZ(-1);
        } else {
            v.setZ(0);
        }

        return v;
    }
}
