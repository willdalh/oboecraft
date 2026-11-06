package io.github.willdalh.oboecraft.core.srs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.settings.LevelSettings;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LockTimerTest {
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final Settings SETTINGS = Settings.DEFAULTS;

    private static Optional<LockTimer> timer(SrsState state, Instant now, Settings settings) {
        return DisplayRules.of(state, now, settings) instanceof DisplayState.Timed timed
                ? Optional.of(timed.timer()) : Optional.empty();
    }

    @Test
    void aPassStartsAFullTimerWhichDrainsWithoutChangingTheState() {
        SrsState passed = SrsRules.pass(SrsState.Leveled.NEW, NOW, SETTINGS);
        assertEquals(1.0, timer(passed, NOW, SETTINGS).orElseThrow().fractionRemaining());
        LockTimer halfway = timer(passed, NOW.plusSeconds(300), SETTINGS).orElseThrow();
        assertEquals(Duration.ofMinutes(5), halfway.remaining());
        assertEquals(0.5, halfway.fractionRemaining());
        assertFalse(halfway.closeToLocking());
        assertTrue(timer(passed, NOW.plusSeconds(600), SETTINGS).isEmpty());
        assertTrue(timer(passed, NOW.plusSeconds(601), SETTINGS).isEmpty());
    }

    @Test
    void theMarkerStartsExactlyAtTheFinalTenthAndEndsAtExpiry() {
        SrsState passed = SrsRules.pass(SrsState.Leveled.NEW, NOW, SETTINGS);
        assertFalse(timer(passed, NOW.plusSeconds(540).minusNanos(1), SETTINGS)
                .orElseThrow().closeToLocking());
        assertTrue(timer(passed, NOW.plusSeconds(540), SETTINGS).orElseThrow().closeToLocking());
        assertTrue(timer(passed, NOW.plusSeconds(600).minusNanos(1), SETTINGS)
                .orElseThrow().closeToLocking());
        assertTrue(timer(passed, NOW.plusSeconds(600), SETTINGS).isEmpty());
    }

    @Test
    void newMissingTimestampAndMasteredStatesHaveNoTimer() {
        for (SrsState state : List.of(SrsState.Leveled.NEW,
                new SrsState.Leveled(0, Optional.of(NOW.plusSeconds(60))),
                new SrsState.Leveled(4, Optional.empty()),
                new SrsState.Leveled(SETTINGS.topLevel(), Optional.empty()),
                new SrsState.Leveled(SETTINGS.topLevel() + 1, Optional.of(NOW.plusSeconds(60))))) {
            assertTrue(timer(state, NOW, SETTINGS).isEmpty());
        }
    }

    @Test
    void usesTheConfiguredIntervalAndSupportsSubsecondAndVeryLongIntervals() {
        for (Duration interval : List.of(Duration.ofNanos(100), Duration.ofDays(1_000_000))) {
            Settings settings = Settings.DEFAULTS.withLevels(List.of(new LevelSettings(interval, 1)));
            SrsState passed = SrsRules.pass(SrsState.Leveled.NEW, NOW, settings);
            LockTimer timer = timer(passed, NOW.plus(interval.dividedBy(2)), settings).orElseThrow();
            assertEquals(0.5, timer.fractionRemaining(), 0.000001);
            assertEquals(interval, timer.interval());
            assertTrue(timer(passed, NOW.plus(interval.minus(interval.dividedBy(10))), settings)
                    .orElseThrow().closeToLocking());
        }
    }

    @Test
    void aBackwardClockJumpCannotOverfillTheBar() {
        SrsState passed = SrsRules.pass(SrsState.Leveled.NEW, NOW, SETTINGS);
        LockTimer timer = timer(passed, NOW.minusSeconds(60), SETTINGS).orElseThrow();
        assertEquals(1.0, timer.fractionRemaining());
        assertFalse(timer.closeToLocking());
    }
}
