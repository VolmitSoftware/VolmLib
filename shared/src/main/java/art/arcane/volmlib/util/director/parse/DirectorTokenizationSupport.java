package art.arcane.volmlib.util.director.parse;

import art.arcane.volmlib.util.decree.DecreeSystemSupport;

import java.util.List;

public final class DirectorTokenizationSupport {
    private DirectorTokenizationSupport() {
    }

    public static List<String> tokenize(String[] args) {
        return DecreeSystemSupport.enhanceArgs(args, true);
    }

    public static List<String> tokenize(String[] args, boolean trim) {
        return DecreeSystemSupport.enhanceArgs(args, trim);
    }
}
