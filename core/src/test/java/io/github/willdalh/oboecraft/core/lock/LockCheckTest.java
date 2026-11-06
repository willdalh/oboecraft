package io.github.willdalh.oboecraft.core.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LockCheckTest {
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final SrsState UNLOCKED = new SrsState.Leveled(1, Optional.of(NOW.plusSeconds(60)));
    private static final SrsState LOCKED = new SrsState.Leveled(1, Optional.of(NOW));

    private static LockDecision check(SrsState state, boolean locksActive) {
        return LockCheck.check(state, NOW, locksActive, Settings.DEFAULTS);
    }

    @Test
    void everythingIsAllowedWhenLocksArentActive() {
        assertEquals(LockDecision.ALLOW, check(LOCKED, false));
        assertEquals(LockDecision.ALLOW, check(SrsState.Leveled.NEW, false));
    }

    @Test
    void anUnlockedItemIsAllowed() {
        assertEquals(LockDecision.ALLOW, check(UNLOCKED, true));
    }

    @Test
    void aLockedItemOpensTheAnswerMenu() {
        assertEquals(LockDecision.REVIEW, check(LOCKED, true));
    }

    @Test
    void aNewItemOpensTheAnswerMenu() {
        assertEquals(LockDecision.REVIEW, check(SrsState.Leveled.NEW, true));
    }
}
