package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.concurrent.ThreadLocalRandom;

public class ByteHandlerBase implements DirectorParameterHandler<Byte> {
    @Override
    public KList<Byte> getPossibilities() {
        return null;
    }

    @Override
    public String toString(Byte value) {
        return value.toString();
    }

    @Override
    public Byte parse(String in, boolean force) throws DirectorParsingException {
        try {
            return Byte.parseByte(in);
        } catch (Throwable e) {
            throw new DirectorParsingException("Unable to parse byte \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Byte.class) || type.equals(byte.class);
    }

    @Override
    public String getRandomDefault() {
        return ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE + 1) + "";
    }
}
