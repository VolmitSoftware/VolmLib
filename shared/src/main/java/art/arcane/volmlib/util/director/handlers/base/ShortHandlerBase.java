package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class ShortHandlerBase implements DirectorParameterHandler<Short> {
    @Override
    public KList<Short> getPossibilities() {
        return null;
    }

    @Override
    public double getMultiplier(AtomicReference<String> value) {
        return DirectorParameterHandler.super.getMultiplier(value);
    }

    @Override
    public Short parse(String in, boolean force) throws DirectorParsingException {
        try {
            AtomicReference<String> r = new AtomicReference<>(in);
            double m = getMultiplier(r);
            return (short) (Short.valueOf(r.get()).doubleValue() * m);
        } catch (Throwable e) {
            throw new DirectorParsingException("Unable to parse short \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Short.class) || type.equals(short.class);
    }

    @Override
    public String toString(Short value) {
        return value.toString();
    }

    @Override
    public String getRandomDefault() {
        return ThreadLocalRandom.current().nextInt(100) + "";
    }
}
