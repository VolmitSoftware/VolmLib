package art.arcane.volmlib.util.director.parse;

@FunctionalInterface
public interface DirectorParser<T> {
    DirectorValue<T> parse(String input);
}
