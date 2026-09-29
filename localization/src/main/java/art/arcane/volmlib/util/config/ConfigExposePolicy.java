package art.arcane.volmlib.util.config;

import java.lang.reflect.Field;

public interface ConfigExposePolicy {
    ConfigExposePolicy ALL = (sourceTag, path, field, value) -> true;

    boolean expose(String sourceTag, String path, Field field, Object value);
}
