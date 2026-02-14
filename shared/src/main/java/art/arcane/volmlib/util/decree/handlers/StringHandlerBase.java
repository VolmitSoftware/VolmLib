package art.arcane.volmlib.util.decree.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared behavior for plugin-specific decree string handlers.
 */
public class StringHandlerBase implements DecreeParameterHandler<String> {
    private static final String[] DEFAULTS = {"text", "string", "blah", "derp", "yolo"};

    @Override
    public KList<String> getPossibilities() {
        return null;
    }

    @Override
    public String toString(String s) {
        return s;
    }

    @Override
    public String parse(String in, boolean force) throws DecreeParsingException {
        return in;
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(String.class);
    }

    @Override
    public String getRandomDefault() {
        return DEFAULTS[ThreadLocalRandom.current().nextInt(DEFAULTS.length)];
    }
}
