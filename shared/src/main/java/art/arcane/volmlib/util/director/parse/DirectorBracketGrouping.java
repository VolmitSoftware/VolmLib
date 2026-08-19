package art.arcane.volmlib.util.director.parse;

import java.util.ArrayList;
import java.util.List;

public final class DirectorBracketGrouping {
    private DirectorBracketGrouping() {
    }

    public record Result(List<String> tokens, String unclosedKey, boolean tailGrouped) {
    }

    public static Result group(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new Result(List.of(), null, false);
        }

        List<String> out = new ArrayList<>(tokens.size());
        String unclosedKey = null;
        boolean tailGrouped = false;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token == null) {
                continue;
            }

            int split = token.indexOf('=');
            boolean opener = split >= 0
                    && split + 1 < token.length()
                    && token.charAt(split + 1) == '['
                    && !containsWhitespace(token);
            if (!opener) {
                out.add(token);
                continue;
            }

            String key = token.substring(0, split);
            String value = token.substring(split + 1);

            if (value.endsWith("]")) {
                String inner = value.substring(1, value.length() - 1);
                if (isHexColorLiteral(inner)) {
                    out.add(token);
                } else {
                    out.add(key + "=" + inner);
                    tailGrouped = i == tokens.size() - 1;
                }
                continue;
            }

            if (value.indexOf(']') >= 0) {
                out.add(token);
                continue;
            }

            int closer = -1;
            for (int j = i + 1; j < tokens.size(); j++) {
                String candidate = tokens.get(j);
                if (candidate != null && candidate.endsWith("]")) {
                    closer = j;
                    break;
                }
            }

            if (closer < 0) {
                unclosedKey = key;
                tailGrouped = true;
                break;
            }

            StringBuilder joined = new StringBuilder(value);
            for (int j = i + 1; j <= closer; j++) {
                String part = tokens.get(j);
                if (part == null) {
                    continue;
                }
                joined.append(' ').append(part);
            }
            String merged = joined.toString();
            out.add(key + "=" + merged.substring(1, merged.length() - 1));
            tailGrouped = closer == tokens.size() - 1;
            i = closer;
        }

        return new Result(List.copyOf(out), unclosedKey, tailGrouped);
    }

    private static boolean containsWhitespace(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isHexColorLiteral(String inner) {
        if (inner.length() != 6) {
            return false;
        }

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }

        return true;
    }
}
