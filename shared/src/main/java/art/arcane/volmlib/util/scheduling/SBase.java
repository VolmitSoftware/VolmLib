package art.arcane.volmlib.util.scheduling;

public abstract class SBase implements Runnable {
    protected SBase() {
        schedule(0);
    }

    protected SBase(int delay) {
        schedule(delay);
    }

    protected abstract void schedule(int delay);
}
