package art.arcane.volmlib.util.decree.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

import java.util.concurrent.ThreadLocalRandom;

public class BooleanHandlerBase implements DecreeParameterHandler<Boolean> {
    @Override
    public KList<Boolean> getPossibilities() {
        return null;
    }

    @Override
    public String toString(Boolean value) {
        return value.toString();
    }

    @Override
    public Boolean parse(String in, boolean force) throws DecreeParsingException {
        try {
            if (in.equals("null") || in.equals("other") || in.equals("flip")) {
                return null;
            }

            return Boolean.parseBoolean(in);
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to parse boolean \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Boolean.class) || type.equals(boolean.class);
    }

    @Override
    public String getRandomDefault() {
        return ThreadLocalRandom.current().nextBoolean() + "";
    }
}
