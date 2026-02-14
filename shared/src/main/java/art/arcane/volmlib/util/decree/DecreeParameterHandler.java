package art.arcane.volmlib.util.decree;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unchecked")
public interface DecreeParameterHandler<T> extends DecreeParameterHandlerType {
    KList<T> getPossibilities();

    default boolean isDummy() {
        return false;
    }

    String toString(T t);

    default String toStringForce(Object t) {
        return toString((T) t);
    }

    default T parse(String in) throws DecreeParsingException {
        return parse(in, false);
    }

    T parse(String in, boolean force) throws DecreeParsingException;

    boolean supports(Class<?> type);

    default KList<T> getPossibilities(String input) {
        return new KList<>(DecreeParameterSupport.getPossibilities(input, getPossibilities(), v -> toString(v).trim()));
    }

    default String getRandomDefault() {
        return "NOEXAMPLE";
    }

    default double getMultiplier(AtomicReference<String> g) {
        return DecreeParameterSupport.getMultiplier(g);
    }
}
