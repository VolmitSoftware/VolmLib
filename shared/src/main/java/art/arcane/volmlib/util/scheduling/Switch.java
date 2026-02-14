package art.arcane.volmlib.util.scheduling;

public class Switch {
    private volatile boolean flipped;

    public Switch() {
        flipped = false;
    }

    public void flip() {
        flipped = true;
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void reset() {
        flipped = false;
    }
}
