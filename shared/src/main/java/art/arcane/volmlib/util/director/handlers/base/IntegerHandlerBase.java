package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class IntegerHandlerBase implements DirectorParameterHandler<Integer> {
    @Override
    public KList<Integer> getPossibilities() {
        return null;
    }

    @Override
    public double getMultiplier(AtomicReference<String> value) {
        return DirectorParameterHandler.super.getMultiplier(value);
    }

    @Override
    public Integer parse(String in, boolean force) throws DirectorParsingException {
        try {
            AtomicReference<String> r = new AtomicReference<>(in);
            double m = getMultiplier(r);
            return (int) (Integer.valueOf(r.get()).doubleValue() * m);
        } catch (Throwable e) {
            throw new DirectorParsingException("Unable to parse integer \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Integer.class) || type.equals(int.class);
    }

    @Override
    public String toString(Integer value) {
        return value.toString();
    }

    @Override
    public String getRandomDefault() {
        return ThreadLocalRandom.current().nextInt(100) + "";
    }
}
