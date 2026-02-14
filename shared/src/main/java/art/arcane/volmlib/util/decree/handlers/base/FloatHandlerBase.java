package art.arcane.volmlib.util.decree.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class FloatHandlerBase implements DecreeParameterHandler<Float> {
    @Override
    public KList<Float> getPossibilities() {
        return null;
    }

    @Override
    public double getMultiplier(AtomicReference<String> value) {
        return DecreeParameterHandler.super.getMultiplier(value);
    }

    @Override
    public Float parse(String in, boolean force) throws DecreeParsingException {
        try {
            AtomicReference<String> r = new AtomicReference<>(in);
            double m = getMultiplier(r);
            return (float) (Float.parseFloat(r.get()) * m);
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to parse float \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Float.class) || type.equals(float.class);
    }

    @Override
    public String toString(Float value) {
        return value.toString();
    }

    @Override
    public String getRandomDefault() {
        return String.format(Locale.ROOT, "%.1f", ThreadLocalRandom.current().nextDouble(0, 99.99));
    }
}
