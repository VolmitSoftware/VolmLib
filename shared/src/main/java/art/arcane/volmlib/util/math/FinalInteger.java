package art.arcane.volmlib.util.math;

import art.arcane.volmlib.util.scheduling.Wrapper;

public class FinalInteger extends Wrapper<Integer> {
    public FinalInteger(Integer value) {
        super(value);
    }

    public void add(int value) {
        set(get() + value);
    }

    public void sub(int value) {
        set(get() - value);
    }
}
