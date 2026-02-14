package art.arcane.volmlib.util.decree.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

import java.util.concurrent.ThreadLocalRandom;

public class ByteHandlerBase implements DecreeParameterHandler<Byte> {
    @Override
    public KList<Byte> getPossibilities() {
        return null;
    }

    @Override
    public String toString(Byte value) {
        return value.toString();
    }

    @Override
    public Byte parse(String in, boolean force) throws DecreeParsingException {
        try {
            return Byte.parseByte(in);
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to parse byte \"" + in + "\"");
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
