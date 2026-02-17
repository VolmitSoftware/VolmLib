package art.arcane.volmlib.util.director;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unchecked")
public interface DirectorParameterHandler<T> extends DirectorParameterHandlerType {
    KList<T> getPossibilities();

    default boolean isDummy() {
        return false;
    }

    String toString(T t);

    default String toStringForce(Object t) {
        return toString((T) t);
    }

    default T parse(String in) throws DirectorParsingException {
        return parse(in, false);
    }

    T parse(String in, boolean force) throws DirectorParsingException;

    boolean supports(Class<?> type);

    default KList<T> getPossibilities(String input) {
        return new KList<>(DirectorParameterSupport.getPossibilities(input, getPossibilities(), v -> toString(v).trim()));
    }

    default String getRandomDefault() {
        return "NOEXAMPLE";
    }

    default double getMultiplier(AtomicReference<String> g) {
        return DirectorParameterSupport.getMultiplier(g);
    }
}
