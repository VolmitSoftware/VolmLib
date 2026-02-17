package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public abstract class BlockVectorHandlerBase implements DirectorParameterHandler<BlockVector> {
    protected abstract boolean isSenderPlayer();

    protected abstract BlockVector getSenderBlockVector();

    protected abstract BlockVector getLookBlockVector();

    protected abstract List<?> playerPossibilities(String query);

    protected abstract String format(double value);

    @Override
    public KList<BlockVector> getPossibilities() {
        KList<BlockVector> options = new KList<>();
        if (isSenderPlayer()) {
            options.add(getSenderBlockVector());
        }

        return options;
    }

    @Override
    public String toString(BlockVector value) {
        if (value.getY() == 0) {
            return format(value.getBlockX()) + "," + format(value.getBlockZ());
        }

        return format(value.getBlockX()) + "," + format(value.getBlockY()) + "," + format(value.getBlockZ());
    }

    @Override
    public BlockVector parse(String in, boolean force) throws DirectorParsingException {
        try {
            if (in.contains(",")) {
                String[] comp = in.split("\\Q,\\E");

                if (comp.length == 2) {
                    return new BlockVector(Integer.parseInt(comp[0].trim()), 0, Integer.parseInt(comp[1].trim()));
                } else if (comp.length == 3) {
                    return new BlockVector(
                            Integer.parseInt(comp[0].trim()),
                            Integer.parseInt(comp[1].trim()),
                            Integer.parseInt(comp[2].trim())
                    );
                }

                throw new DirectorParsingException("Could not parse components for vector. You have " + comp.length + " components. Expected 2 or 3.");
            }

            if (in.equalsIgnoreCase("here") || in.equalsIgnoreCase("me") || in.equalsIgnoreCase("self")) {
                if (!isSenderPlayer()) {
                    throw new DirectorParsingException("You cannot specify me,self,here as a console.");
                }

                return getSenderBlockVector();
            }

            if (in.equalsIgnoreCase("look") || in.equalsIgnoreCase("cursor") || in.equalsIgnoreCase("crosshair")) {
                if (!isSenderPlayer()) {
                    throw new DirectorParsingException("You cannot specify look,cursor,crosshair as a console.");
                }

                return getLookBlockVector();
            }

            if (in.trim().toLowerCase().startsWith("player:")) {
                String query = in.trim().split("\\Q:\\E")[1];
                List<?> matches = playerPossibilities(query);

                if (matches != null && !matches.isEmpty() && matches.get(0) instanceof Player player) {
                    return player.getLocation().toVector().toBlockVector();
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
        return type.equals(BlockVector.class);
    }

    @Override
    public String getRandomDefault() {
        return ThreadLocalRandom.current().nextBoolean() ? "0,0" : "0,0,0";
    }
}
