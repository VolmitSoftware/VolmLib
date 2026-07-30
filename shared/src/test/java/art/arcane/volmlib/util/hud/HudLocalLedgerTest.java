package art.arcane.volmlib.util.hud;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HudLocalLedgerTest {
  private static final String KEY = "uuid|ACTION_BAR";

  @Test
  public void test_claim_emptyKey_grants() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim(KEY, 1L, HudPriority.AMBIENT, 100L, 1500L, 100L));
  }

  @Test
  public void test_claim_lowerPriorityChallenger_deniedAndHolderRetained() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim(KEY, 1L, HudPriority.PROGRESS, 100L, 1500L, 100L));
    assertFalse(ledger.claim(KEY, 2L, HudPriority.AMBIENT, 200L, 1500L, 200L));
    assertTrue(ledger.claim(KEY, 1L, HudPriority.PROGRESS, 100L, 1500L, 300L));
  }

  @Test
  public void test_claim_higherPriorityChallenger_preempts() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim(KEY, 1L, HudPriority.AMBIENT, 100L, 1500L, 100L));
    assertTrue(ledger.claim(KEY, 2L, HudPriority.MODAL, 200L, 1500L, 200L));
    assertFalse(ledger.claim(KEY, 1L, HudPriority.AMBIENT, 100L, 1500L, 300L));
  }

  @Test
  public void test_claim_equalPriorityEarlierSince_keepsSlot() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim(KEY, 2L, HudPriority.NOTICE, 500L, 1500L, 500L));
    assertFalse(ledger.claim(KEY, 3L, HudPriority.NOTICE, 600L, 1500L, 600L));
    assertTrue(ledger.claim(KEY, 1L, HudPriority.NOTICE, 400L, 1500L, 700L));
  }

  @Test
  public void test_claim_reassertRefreshesExpiry() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim(KEY, 1L, HudPriority.AMBIENT, 0L, 500L, 0L));
    assertTrue(ledger.claim(KEY, 1L, HudPriority.AMBIENT, 0L, 500L, 400L));
    assertFalse(ledger.claim(KEY, 2L, HudPriority.AMBIENT, 800L, 500L, 800L));
  }

  @Test
  public void test_claim_expiredHolder_replaced() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim(KEY, 1L, HudPriority.MODAL, 0L, 500L, 0L));
    assertTrue(ledger.claim(KEY, 2L, HudPriority.AMBIENT, 600L, 1500L, 600L));
  }

  @Test
  public void test_release_ownerOnly() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim(KEY, 1L, HudPriority.AMBIENT, 100L, 1500L, 100L));
    assertFalse(ledger.release(KEY, 2L));
    assertTrue(ledger.release(KEY, 1L));
    assertFalse(ledger.release(KEY, 1L));
    assertTrue(ledger.claim(KEY, 2L, HudPriority.AMBIENT, 200L, 1500L, 200L));
  }

  @Test
  public void test_clearPrefix_removesOnlyMatchingKeys() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim("a|ACTION_BAR", 1L, HudPriority.AMBIENT, 100L, 1500L, 100L));
    assertTrue(ledger.claim("b|ACTION_BAR", 2L, HudPriority.AMBIENT, 100L, 1500L, 100L));
    ledger.clearPrefix("a|");
    assertTrue(ledger.claim("a|ACTION_BAR", 3L, HudPriority.AMBIENT, 200L, 1500L, 200L));
    assertFalse(ledger.claim("b|ACTION_BAR", 3L, HudPriority.AMBIENT, 200L, 1500L, 200L));
  }

  @Test
  public void test_sweep_dropsLongExpiredEntries() {
    HudLocalLedger ledger = new HudLocalLedger();
    assertTrue(ledger.claim(KEY, 1L, HudPriority.MODAL, 0L, 500L, 0L));
    ledger.sweep(70_000L);
    assertTrue(ledger.claim(KEY, 2L, HudPriority.AMBIENT, 70_100L, 1500L, 70_100L));
  }
}
