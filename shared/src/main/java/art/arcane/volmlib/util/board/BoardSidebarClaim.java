package art.arcane.volmlib.util.board;

import java.util.List;
import java.util.UUID;

final class BoardSidebarClaim {
    private static final char SEPARATOR = ':';

    private BoardSidebarClaim() {
    }

    static String create(long sequence, UUID nonce) {
        return sequence + String.valueOf(SEPARATOR) + nonce;
    }

    static boolean isWinner(String candidate, List<Value> values) {
        String winner = null;
        for (Value value : values) {
            if (!value.enabled() || !isValid(value.token())) {
                continue;
            }
            if (winner == null || compare(value.token(), winner) > 0) {
                winner = value.token();
            }
        }
        return candidate.equals(winner);
    }

    private static boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        int separator = token.indexOf(SEPARATOR);
        if (separator <= 0 || separator == token.length() - 1) {
            return false;
        }
        try {
            Long.parseLong(token.substring(0, separator));
            UUID.fromString(token.substring(separator + 1));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static int compare(String first, String second) {
        int firstSeparator = first.indexOf(SEPARATOR);
        int secondSeparator = second.indexOf(SEPARATOR);
        long firstSequence = Long.parseLong(first.substring(0, firstSeparator));
        long secondSequence = Long.parseLong(second.substring(0, secondSeparator));
        int sequenceComparison = Long.compare(firstSequence, secondSequence);
        if (sequenceComparison != 0) {
            return sequenceComparison;
        }
        return first.substring(firstSeparator + 1).compareTo(second.substring(secondSeparator + 1));
    }

    record Value(String token, boolean enabled) {
    }
}
