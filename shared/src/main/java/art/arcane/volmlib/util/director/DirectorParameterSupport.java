package art.arcane.volmlib.util.director;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class DirectorParameterSupport {
    private DirectorParameterSupport() {
    }

    public static <T> List<T> getPossibilities(String input, List<T> possible, Function<T, String> toString) {
        List<T> matches = new ArrayList<>();
        String trimmedInput = input.trim();

        if (trimmedInput.isEmpty()) {
            return possible == null ? matches : possible;
        }

        if (possible == null || possible.isEmpty()) {
            return matches;
        }

        String loweredInput = trimmedInput.toLowerCase();

        for (T value : possible) {
            String rendered = toString.apply(value).trim();
            String loweredRendered = rendered.toLowerCase();

            if (rendered.equalsIgnoreCase(trimmedInput)
                    || loweredRendered.contains(loweredInput)
                    || loweredInput.contains(loweredRendered)) {
                matches.add(value);
            }
        }

        return matches;
    }

    public static double getMultiplier(AtomicReference<String> inputRef) {
        double multiplier = 1;
        String in = inputRef.get();
        boolean valid = true;

        while (valid) {
            boolean trim = false;
            if (in.toLowerCase().endsWith("k")) {
                multiplier *= 1000;
                trim = true;
            } else if (in.toLowerCase().endsWith("m")) {
                multiplier *= 1000000;
                trim = true;
            } else if (in.toLowerCase().endsWith("h")) {
                multiplier *= 100;
                trim = true;
            } else if (in.toLowerCase().endsWith("c")) {
                multiplier *= 16;
                trim = true;
            } else if (in.toLowerCase().endsWith("r")) {
                multiplier *= (16 * 32);
                trim = true;
            } else {
                valid = false;
            }

            if (trim) {
                in = in.substring(0, in.length() - 1);
            }
        }

        inputRef.set(in);
        return multiplier;
    }
}
