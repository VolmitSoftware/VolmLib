package art.arcane.volmlib.util.localization;

@FunctionalInterface
public interface PluralSelector {
    String select(String locale, Number quantity);

    static PluralSelector oneOther() {
        return (locale, quantity) -> Double.compare(quantity.doubleValue(), 1D) == 0 ? "one" : "other";
    }
}
