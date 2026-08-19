package art.arcane.volmlib.util.board;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoardSidebarClaimTest {
    @Test
    public void newestEnabledClaimWins() {
        String older = BoardSidebarClaim.create(10L, new UUID(0L, 1L));
        String newer = BoardSidebarClaim.create(11L, new UUID(0L, 1L));
        List<BoardSidebarClaim.Value> claims = List.of(
                new BoardSidebarClaim.Value(older, true),
                new BoardSidebarClaim.Value(newer, true));

        assertFalse(BoardSidebarClaim.isWinner(older, claims));
        assertTrue(BoardSidebarClaim.isWinner(newer, claims));
    }

    @Test
    public void nonceBreaksSequenceTiesDeterministically() {
        String lower = BoardSidebarClaim.create(10L, new UUID(0L, 1L));
        String higher = BoardSidebarClaim.create(10L, new UUID(0L, 2L));
        List<BoardSidebarClaim.Value> claims = List.of(
                new BoardSidebarClaim.Value(higher, true),
                new BoardSidebarClaim.Value(lower, true));

        assertFalse(BoardSidebarClaim.isWinner(lower, claims));
        assertTrue(BoardSidebarClaim.isWinner(higher, claims));
    }

    @Test
    public void disabledAndMalformedClaimsCannotStrandOwnership() {
        String previous = BoardSidebarClaim.create(10L, new UUID(0L, 1L));
        String staleWinner = BoardSidebarClaim.create(20L, new UUID(0L, 1L));
        List<BoardSidebarClaim.Value> claims = List.of(
                new BoardSidebarClaim.Value(previous, true),
                new BoardSidebarClaim.Value(staleWinner, false),
                new BoardSidebarClaim.Value("broken", true));

        assertTrue(BoardSidebarClaim.isWinner(previous, claims));
        assertFalse(BoardSidebarClaim.isWinner(staleWinner, claims));
    }

    @Test
    public void previousClaimWinsAgainAfterWinnerIsRemoved() {
        String previous = BoardSidebarClaim.create(10L, new UUID(0L, 1L));
        String temporaryWinner = BoardSidebarClaim.create(20L, new UUID(0L, 1L));

        assertFalse(BoardSidebarClaim.isWinner(previous, List.of(
                new BoardSidebarClaim.Value(previous, true),
                new BoardSidebarClaim.Value(temporaryWinner, true))));
        assertTrue(BoardSidebarClaim.isWinner(previous, List.of(
                new BoardSidebarClaim.Value(previous, true))));
    }

    @Test
    public void winnerSelectionIsSafeUnderConcurrentReads() throws Exception {
        List<BoardSidebarClaim.Value> claims = new ArrayList<>();
        String winner = null;
        for (int index = 0; index < 64; index++) {
            String token = BoardSidebarClaim.create(index, new UUID(0L, index + 1L));
            claims.add(new BoardSidebarClaim.Value(token, true));
            winner = token;
        }

        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean();
        List<Thread> readers = new ArrayList<>();
        String expectedWinner = winner;
        for (int index = 0; index < 8; index++) {
            Thread reader = new Thread(() -> {
                try {
                    start.await();
                    for (int pass = 0; pass < 1_000; pass++) {
                        if (!BoardSidebarClaim.isWinner(expectedWinner, claims)) {
                            failed.set(true);
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failed.set(true);
                }
            });
            readers.add(reader);
            reader.start();
        }

        start.countDown();
        for (Thread reader : readers) {
            reader.join();
        }
        assertFalse(failed.get());
    }
}
