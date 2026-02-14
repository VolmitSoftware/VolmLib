package art.arcane.volmlib.util.data.palette;

import java.util.List;

public interface Palette<T> {
    int idFor(T value);

    T valueFor(int id);

    int getSize();

    void read(List<T> fromList);

    void write(List<T> toList);
}
