package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.util.List;

public abstract class VectorHandlerBase implements DirectorParameterHandler<Vector> {
    protected abstract boolean isSenderPlayer();

    protected abstract Vector getSenderVector();

    protected abstract Vector getLookVector();

    protected abstract List<?> playerPossibilities(String query);

    protected abstract String format(double value);

    @Override
    public KList<Vector> getPossibilities() {
        return null;
    }

    @Override
    public String toString(Vector value) {
        if (value.getY() == 0) {
            return format(value.getX()) + "," + format(value.getZ());
        }

        return format(value.getX()) + "," + format(value.getY()) + "," + format(value.getZ());
    }

    @Override
    public Vector parse(String in, boolean force) throws DirectorParsingException {
        try {
            if (in.contains(",")) {
                String[] comp = in.split("\\Q,\\E");

                if (comp.length == 2) {
                    return new BlockVector(Double.parseDouble(comp[0].trim()), 0, Double.parseDouble(comp[1].trim()));
                } else if (comp.length == 3) {
                    return new BlockVector(
                            Double.parseDouble(comp[0].trim()),
                            Double.parseDouble(comp[1].trim()),
                            Double.parseDouble(comp[2].trim())
                    );
                }

                throw new DirectorParsingException("Could not parse components for vector. You have " + comp.length + " components. Expected 2 or 3.");
            }

            if (in.equalsIgnoreCase("here") || in.equalsIgnoreCase("me") || in.equalsIgnoreCase("self")) {
                if (!isSenderPlayer()) {
                    throw new DirectorParsingException("You cannot specify me,self,here as a console.");
                }

                return getSenderVector();
            }

            if (in.equalsIgnoreCase("look") || in.equalsIgnoreCase("cursor") || in.equalsIgnoreCase("crosshair")) {
                if (!isSenderPlayer()) {
                    throw new DirectorParsingException("You cannot specify look,cursor,crosshair as a console.");
                }

                return getLookVector();
            }

            if (in.trim().toLowerCase().startsWith("player:")) {
                String query = in.trim().split("\\Q:\\E")[1];
                List<?> matches = playerPossibilities(query);

                if (matches != null && !matches.isEmpty() && matches.get(0) instanceof Player player) {
                    return player.getLocation().toVector();
                }

                throw new DirectorParsingException("Cannot find player: " + query);
            }
        } catch (DirectorParsingException e) {
            throw e;
        } catch (Throwable e) {
            throw new DirectorParsingException("Unable to get Vector for \"" + in + "\" because of an uncaught exception: " + e);
        }

        return null;
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Vector.class);
    }

    @Override
    public String getRandomDefault() {
        return new KList<>("here", "0,0,0", "0,0", "look", "player:<name>").getRandom();
    }
}
