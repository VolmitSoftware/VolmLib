package art.arcane.volmlib.util.decree.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

public class DummyHandlerBase implements DecreeParameterHandler<Object> {
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
    public Object parse(String in, boolean force) throws DecreeParsingException {
        return null;
    }

    @Override
    public boolean supports(Class<?> type) {
        return false;
    }
}
