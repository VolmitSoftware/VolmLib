package art.arcane.volmlib.util.scheduling;

public class Wrapper<T> {
    private T value;

    public Wrapper(T value) {
        set(value);
    }

    public void set(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wrapper<?> other)) {
            return false;
        }

        if (value == null) {
            return other.value == null;
        }

        return value.equals(other.value);
    }

    @Override
    public String toString() {
        if (value != null) {
            return get().toString();
        }

        return super.toString() + " (null)";
    }
}
