package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class DummyHandlerBase implements DirectorParameterHandler<Object> {
    @Override
    public KList<Object> getPossibilities() {
        return null;
    }

    @Override
    public boolean isDummy() {
        return true;
    }

    @Override
    public String toString(Object value) {
        return null;
    }

    @Override
    public Object parse(String in, boolean force) throws DirectorParsingException {
        return null;
    }

    @Override
    public boolean supports(Class<?> type) {
        return false;
    }
}
