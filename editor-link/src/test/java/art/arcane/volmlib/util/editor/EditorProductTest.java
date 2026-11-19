package art.arcane.volmlib.util.editor;

import static art.arcane.volmlib.util.editor.EditorFixtures.ANIMUS;
import static art.arcane.volmlib.util.editor.EditorFixtures.GLOSS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EditorProductTest {
    @Test
    void names_animus_derivedFromTheName() {
        assertEquals("animus", ANIMUS.id());
        assertEquals("/animus", ANIMUS.basePath());
        assertEquals("X-Animus-Peer", ANIMUS.peerHeader());
        assertEquals("X-Animus-Counter", ANIMUS.counterHeader());
        assertEquals("X-Animus-Challenge", ANIMUS.header("Challenge"));
        assertEquals("animus-relay/1 ", ANIMUS.relayChallengePrefix());
        assertEquals("animus-editor-proof/1 ", ANIMUS.proofPrefix());
        assertEquals("Animus-editor-listener", ANIMUS.thread("listener"));
    }

    @Test
    void names_gloss_derivedFromTheName() {
        assertEquals("/gloss", GLOSS.basePath());
        assertEquals("X-Gloss-Peer", GLOSS.peerHeader());
        assertEquals("X-Gloss-Counter", GLOSS.counterHeader());
        assertEquals("gloss-relay/1 ", GLOSS.relayChallengePrefix());
        assertEquals("gloss-editor-proof/1 ", GLOSS.proofPrefix());
    }

    @Test
    void constructor_malformedNames_refused() {
        assertThrows(IllegalArgumentException.class, () -> new EditorProduct("animus", "anm_", "ANM1.", "/x", false));
        assertThrows(IllegalArgumentException.class, () -> new EditorProduct("Animus Two", "anm_", "ANM1.", "/x", false));
        assertThrows(IllegalArgumentException.class, () -> new EditorProduct("Animus", "anm", "ANM1.", "/x", false));
        assertThrows(IllegalArgumentException.class, () -> new EditorProduct("Animus", "anm_", "ANM1", "/x", false));
        assertThrows(NullPointerException.class, () -> new EditorProduct("Animus", "anm_", "ANM1.", null, false));
    }
}
