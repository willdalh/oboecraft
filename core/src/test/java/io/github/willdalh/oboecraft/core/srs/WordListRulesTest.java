package io.github.willdalh.oboecraft.core.srs;

import static org.junit.jupiter.api.Assertions.*;
import io.github.willdalh.oboecraft.core.lock.*;
import io.github.willdalh.oboecraft.core.review.*;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WordListRulesTest {
    private static final Settings SETTINGS = Settings.DEFAULTS;
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test void knownAndIgnoredCannotBeReviewedAndHaveNoTimer() {
        for (SrsState state : new SrsState[] {new SrsState.Known(), new SrsState.Ignored()}) {
            assertEquals(new ReviewStart.NotLocked(), ReviewRules.start(state, NOW, SETTINGS));
            assertEquals(DisplayState.Normal.INSTANCE, DisplayRules.of(state, NOW, SETTINGS));
            assertFalse(DisplayRules.closeToLocking(state, NOW, SETTINGS));
            assertTrue(SrsRules.canRevert(state, SETTINGS));
        }
    }

    @Test void ignoredDeniesEveryInteractionExceptDroppingEvenWithDropLocksEnabled() {
        for (Interaction interaction : Interaction.values()) {
            assertEquals(interaction == Interaction.DROP ? LockDecision.ALLOW : LockDecision.DENY,
                    LockCheck.check(new SrsState.Ignored(), interaction, NOW, true, Settings.DEFAULTS.withDropCountsAsInteraction(true)));
            assertEquals(interaction == Interaction.DROP ? LockDecision.ALLOW : LockDecision.DENY,
                    LockCheck.check(new SrsState.Ignored(), interaction, NOW, true, SETTINGS));
            assertEquals(LockDecision.ALLOW,
                    LockCheck.check(new SrsState.Known(), interaction, NOW, true, SETTINGS));
            assertEquals(LockDecision.ALLOW,
                    LockCheck.check(new SrsState.Ignored(), interaction, NOW, false, SETTINGS));
        }
    }

    @Test void pickupDoesNotReviewNewItems() {
        assertEquals(LockDecision.ALLOW,
                LockCheck.check(SrsState.Leveled.NEW, Interaction.PICK_UP, NOW, true, SETTINGS));
    }

    @Test void onlyMasteredLevelsCanBeReverted() {
        for (int level = 0; level <= SETTINGS.topLevel() + 1; level++) {
            assertEquals(level >= SETTINGS.topLevel(),
                    SrsRules.canRevert(new SrsState.Leveled(level, Optional.empty()), SETTINGS));
        }
    }
}
