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
        Settings settings = Settings.DEFAULTS.withLevels(List.of(new LevelSettings(Duration.ofSeconds(30), 1),
                new LevelSettings(Duration.ofSeconds(120), 1)));
        DisplayState.Timed timed = assertInstanceOf(DisplayState.Timed.class, DisplayRules.of(state, NOW, settings));
        assertEquals(Duration.ofSeconds(120), timed.timer().interval());
        assertEquals(DisplayState.Normal.INSTANCE, DisplayRules.of(state, NOW,
                Settings.DEFAULTS.withLevels(List.of(new LevelSettings(Duration.ofSeconds(30), 1)))));
    }

    @Test
    void markerOnlyPathAgreesWithFullDisplayAtEveryBoundary() {
        for (Duration interval : List.of(Duration.ofNanos(100), Duration.ofMinutes(10), Duration.ofDays(1_000_000))) {
            Settings settings = Settings.DEFAULTS.withLevels(List.of(new LevelSettings(interval, 1)));
            Instant end = NOW.plus(interval);
            Instant warning = end.minus(interval.dividedBy(10));
            for (SrsState state : List.of(SrsState.Leveled.NEW,
                    new SrsState.Leveled(1, Optional.of(end)),
                    new SrsState.Leveled(1, Optional.empty()), new SrsState.Leveled(2, Optional.empty()))) {
                for (Instant time : List.of(NOW.minusSeconds(1), NOW, warning.minusNanos(1), warning,
                        end.minusNanos(1), end, end.plusNanos(1))) {
                    DisplayState display = DisplayRules.of(state, time, settings);
                    SlotMarker expected = state instanceof SrsState.Leveled leveled && leveled.level() == 0
                            ? SlotMarker.NEW : display instanceof DisplayState.Hidden ? SlotMarker.OVERDUE
                            : display instanceof DisplayState.Timed timed && timed.timer().closeToLocking()
                            ? SlotMarker.NEAR_LOCK : SlotMarker.NONE;
                    assertEquals(expected, DisplayRules.slotMarker(state, time, settings));
                }
            }
        }
    }
    @Test
    void markerCoversBansAndChangedLevelSettings() {
        Settings settings = Settings.DEFAULTS;
        for (SrsState state : List.of(new SrsState.Known(), new SrsState.Ignored(),
                new SrsState.Leveled(settings.topLevel(), Optional.empty()),
                new SrsState.Leveled(settings.topLevel() + 1, Optional.of(NOW)))) {
            assertEquals(SlotMarker.NONE, DisplayRules.slotMarker(state, NOW, settings));
        }
        var banned = new SrsState.Leveled(1, Optional.of(NOW), true, 0, Optional.of(NOW.plusSeconds(60)));
        assertEquals(SlotMarker.OVERDUE, DisplayRules.slotMarker(banned, NOW, settings));
        var formerlyMastered = new SrsState.Leveled(2, Optional.empty());
        Settings shortLevels = settings.withLevels(List.of(new LevelSettings(Duration.ofMinutes(1), 1)));
        assertEquals(SlotMarker.NONE, DisplayRules.slotMarker(formerlyMastered, NOW, shortLevels));
        assertEquals(SlotMarker.OVERDUE, DisplayRules.slotMarker(formerlyMastered, NOW, settings));
        assertEquals(SlotMarker.NEW, DisplayRules.slotMarker(
                new SrsState.Leveled(0, Optional.of(NOW.plusSeconds(600))), NOW, settings));
    }
}
