package art.arcane.volmlib.util.director.parse;

public final class DirectorStandardParsers {
    private DirectorStandardParsers() {
    }

    public static void registerDefaults(DirectorParserRegistry registry) {
        if (registry == null) {
            return;
        }

        registry.register(String.class, input -> DirectorValue.high(input));
        registry.register(Boolean.class, DirectorStandardParsers::parseBoolean);
        registry.register(boolean.class, DirectorStandardParsers::parseBoolean);
        registry.register(Byte.class, DirectorStandardParsers::parseByte);
        registry.register(byte.class, DirectorStandardParsers::parseByte);
        registry.register(Short.class, DirectorStandardParsers::parseShort);
        registry.register(short.class, DirectorStandardParsers::parseShort);
        registry.register(Integer.class, DirectorStandardParsers::parseInteger);
        registry.register(int.class, DirectorStandardParsers::parseInteger);
        registry.register(Long.class, DirectorStandardParsers::parseLong);
        registry.register(long.class, DirectorStandardParsers::parseLong);
        registry.register(Float.class, DirectorStandardParsers::parseFloat);
        registry.register(float.class, DirectorStandardParsers::parseFloat);
        registry.register(Double.class, DirectorStandardParsers::parseDouble);
        registry.register(double.class, DirectorStandardParsers::parseDouble);
        registry.register(Character.class, DirectorStandardParsers::parseCharacter);
        registry.register(char.class, DirectorStandardParsers::parseCharacter);
    }

    private static DirectorValue<Boolean> parseBoolean(String input) {
        if (input == null) {
            return DirectorValue.invalid(null);
        }

        return switch (input.trim().toLowerCase()) {
            case "true", "t", "yes", "y", "on", "1" -> DirectorValue.high(Boolean.TRUE);
            case "false", "f", "no", "n", "off", "0" -> DirectorValue.high(Boolean.FALSE);
            default -> DirectorValue.invalid(null);
        };
    }

    private static DirectorValue<Byte> parseByte(String input) {
        try {
            return DirectorValue.high(Byte.parseByte(input.trim()));
        } catch (Throwable ignored) {
            return DirectorValue.invalid(null);
        }
    }

    private static DirectorValue<Short> parseShort(String input) {
        try {
            return DirectorValue.high(Short.parseShort(input.trim()));
        } catch (Throwable ignored) {
            return DirectorValue.invalid(null);
        }
    }

    private static DirectorValue<Integer> parseInteger(String input) {
        try {
            return DirectorValue.high(Integer.parseInt(input.trim()));
        } catch (Throwable ignored) {
            return DirectorValue.invalid(null);
        }
    }

    private static DirectorValue<Long> parseLong(String input) {
        try {
            return DirectorValue.high(Long.parseLong(input.trim()));
        } catch (Throwable ignored) {
            return DirectorValue.invalid(null);
        }
    }

    private static DirectorValue<Float> parseFloat(String input) {
        try {
            return DirectorValue.high(Float.parseFloat(input.trim()));
        } catch (Throwable ignored) {
            return DirectorValue.invalid(null);
        }
    }

    private static DirectorValue<Double> parseDouble(String input) {
        try {
            return DirectorValue.high(Double.parseDouble(input.trim()));
        } catch (Throwable ignored) {
            return DirectorValue.invalid(null);
        }
    }

    private static DirectorValue<Character> parseCharacter(String input) {
        if (input == null) {
            return DirectorValue.invalid(null);
        }

        String trimmed = input.trim();
        if (trimmed.length() == 1) {
            return DirectorValue.high(trimmed.charAt(0));
        }

        return DirectorValue.invalid(null);
    }
}
