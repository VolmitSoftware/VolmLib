package art.arcane.volmlib.util.decree.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class DoubleHandlerBase implements DecreeParameterHandler<Double> {
    @Override
    public KList<Double> getPossibilities() {
        return null;
    }

    @Override
    public double getMultiplier(AtomicReference<String> value) {
        return DecreeParameterHandler.super.getMultiplier(value);
    }

    @Override
    public Double parse(String in, boolean force) throws DecreeParsingException {
        try {
            AtomicReference<String> r = new AtomicReference<>(in);
            double m = getMultiplier(r);
            return Double.parseDouble(r.get()) * m;
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to parse double \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Double.class) || type.equals(double.class);
    }

    @Override
    public String toString(Double value) {
        return value.toString();
    }

    @Override
    public String getRandomDefault() {
        return String.format(Locale.ROOT, "%.1f", ThreadLocalRandom.current().nextDouble(0, 99.99));
    }
}
