package art.arcane.volmlib.util.director.parse;

import art.arcane.volmlib.util.director.DirectorSystemSupport;

import java.util.List;

public final class DirectorTokenizationSupport {
    private DirectorTokenizationSupport() {
    }

    public static List<String> tokenize(String[] args) {
        return DirectorSystemSupport.enhanceArgs(args, true);
    }

    public static List<String> tokenize(String[] args, boolean trim) {
        return DirectorSystemSupport.enhanceArgs(args, trim);
    }
}
