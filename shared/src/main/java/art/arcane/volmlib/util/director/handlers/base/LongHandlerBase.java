package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class LongHandlerBase implements DirectorParameterHandler<Long> {
    @Override
    public KList<Long> getPossibilities() {
        return null;
    }

    @Override
    public double getMultiplier(AtomicReference<String> value) {
        return DirectorParameterHandler.super.getMultiplier(value);
    }

    @Override
    public Long parse(String in, boolean force) throws DirectorParsingException {
        try {
            AtomicReference<String> r = new AtomicReference<>(in);
            double m = getMultiplier(r);
            if (m == 1) {
                return Long.parseLong(r.get());
            }

            return (long) (Long.valueOf(r.get()).doubleValue() * m);
        } catch (Throwable e) {
            throw new DirectorParsingException("Unable to parse long \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Long.class) || type.equals(long.class);
    }

    @Override
    public String toString(Long value) {
        return value.toString();
    }

    @Override
    public String getRandomDefault() {
        return ThreadLocalRandom.current().nextInt(100) + "";
    }
}
