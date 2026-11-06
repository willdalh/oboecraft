package io.github.willdalh.oboecraft.core.srs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.willdalh.oboecraft.core.settings.LevelSettings;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DisplayRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void lockedNamesAreHiddenAndMasteredNamesAreNormal() {
        for (SrsState state : List.of(SrsState.Leveled.NEW,
                new SrsState.Leveled(0, Optional.of(NOW.plusSeconds(600))),
                new SrsState.Leveled(1, Optional.empty()),
                new SrsState.Leveled(1, Optional.of(NOW)))) {
            assertEquals(DisplayState.Hidden.INSTANCE, DisplayRules.of(state, NOW, Settings.DEFAULTS));
        }
        assertEquals(DisplayState.Normal.INSTANCE, DisplayRules.of(
                new SrsState.Leveled(9, Optional.empty()), NOW, Settings.DEFAULTS));
    }

    @Test
    void displayUsesConfiguredIntervalsAndTopLevel() {
        var state = new SrsState.Leveled(2, Optional.of(NOW.plusSeconds(120)));
        Settings settings = new Settings(List.of(new LevelSettings(Duration.ofSeconds(30)),
                new LevelSettings(Duration.ofSeconds(120))), false);
        DisplayState.Timed timed = assertInstanceOf(DisplayState.Timed.class, DisplayRules.of(state, NOW, settings));
        assertEquals(Duration.ofSeconds(120), timed.timer().interval());
        assertEquals(DisplayState.Normal.INSTANCE, DisplayRules.of(state, NOW,
                new Settings(List.of(new LevelSettings(Duration.ofSeconds(30))), false)));
    }

    @Test
    void markerOnlyPathAgreesWithFullDisplayAtEveryBoundary() {
        for (Duration interval : List.of(Duration.ofNanos(100), Duration.ofMinutes(10), Duration.ofDays(1_000_000))) {
            Settings settings = new Settings(List.of(new LevelSettings(interval)), false);
            Instant end = NOW.plus(interval);
            Instant warning = end.minus(interval.dividedBy(10));
            for (SrsState state : List.of(SrsState.Leveled.NEW,
                    new SrsState.Leveled(1, Optional.of(end)),
                    new SrsState.Leveled(1, Optional.empty()), new SrsState.Leveled(2, Optional.empty()))) {
                for (Instant time : List.of(NOW.minusSeconds(1), NOW, warning.minusNanos(1), warning,
                        end.minusNanos(1), end, end.plusNanos(1))) {
                    DisplayState display = DisplayRules.of(state, time, settings);
                    boolean expected = display instanceof DisplayState.Timed timed && timed.timer().closeToLocking();
                    assertEquals(expected, DisplayRules.closeToLocking(state, time, settings));
                }
            }
        }
    }
}
