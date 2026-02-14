package art.arcane.volmlib.util.decree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;

public final class DecreeSystemSupport {
    private DecreeSystemSupport() {
    }

    public static List<String> enhanceArgs(String[] args) {
        return enhanceArgs(args, true);
    }

    public static List<String> enhanceArgs(String[] args, boolean trim) {
        List<String> enhanced = new ArrayList<>();

        if (args.length == 0) {
            return enhanced;
        }

        StringBuilder flat = new StringBuilder();
        for (String arg : args) {
            if (trim) {
                if (arg.trim().isEmpty()) {
                    continue;
                }

                flat.append(" ").append(arg.trim());
            } else if (arg.endsWith(" ")) {
                flat.append(" ").append(arg.trim()).append(" ");
            }
        }

        flat = new StringBuilder(flat.length() > 0
                ? trim
                    ? flat.toString().trim().length() > 0 ? flat.substring(1).trim() : flat.toString().trim()
                    : flat.substring(1)
                : flat.toString());

        StringBuilder current = new StringBuilder();
        boolean quoting = false;

        for (int x = 0; x < flat.length(); x++) {
            char i = flat.charAt(x);
            char j = x < flat.length() - 1 ? flat.charAt(x + 1) : i;
            boolean hasNext = x < flat.length();

            if (i == ' ' && !quoting) {
                if (!current.toString().trim().isEmpty() && trim) {
                    enhanced.add(current.toString().trim());
                    current = new StringBuilder();
                }
            } else if (i == '"') {
                if (!quoting && current.length() == 0) {
                    quoting = true;
                } else if (quoting) {
                    quoting = false;

                    if (hasNext && j == ' ') {
                        if (!current.toString().trim().isEmpty() && trim) {
                            enhanced.add(current.toString().trim());
                            current = new StringBuilder();
                        }
                    } else if (!hasNext) {
                        if (!current.toString().trim().isEmpty() && trim) {
                            enhanced.add(current.toString().trim());
                            current = new StringBuilder();
                        }
                    }
                }
            } else {
                current.append(i);
            }
        }

        if (!current.toString().trim().isEmpty() && trim) {
            enhanced.add(current.toString().trim());
        }

        return enhanced;
    }

    public static <H> H getHandler(Collection<H> handlers, Class<?> type, BiPredicate<H, Class<?>> supports) {
        for (H handler : handlers) {
            if (supports.test(handler, type)) {
                return handler;
            }
        }

        return null;
    }
}
