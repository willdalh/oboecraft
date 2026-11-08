package io.github.willdalh.oboecraft.core.srs;

import static org.junit.jupiter.api.Assertions.*;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningOrderTest {
    private static final Settings SETTINGS = Settings.DEFAULTS;
    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    private static SrsState.Leveled leveled(int level, Duration fromNow) {
        return new SrsState.Leveled(level, Optional.of(NOW.plus(fromNow)));
    }

    @Test void onlyLevelsFromOneUpToMasteredAreLearning() {
        for (int level = 0; level <= SETTINGS.topLevel() + 1; level++) {
            assertEquals(level >= 1 && level < SETTINGS.topLevel(),
                    LearningOrder.isLearning(new SrsState.Leveled(level, Optional.empty()), SETTINGS));
        }
        assertFalse(LearningOrder.isLearning(new SrsState.Known(), SETTINGS));
        assertFalse(LearningOrder.isLearning(new SrsState.Ignored(), SETTINGS));
    }

    @Test void urgencyPutsLongestLockedFirstWithBannedAmongThemThenLeastTimeLeft() {
        var noLocksAt = new SrsState.Leveled(3, Optional.empty());
        var lockedLongest = leveled(2, Duration.ofHours(-2));
        var banned = new SrsState.Leveled(1, Optional.of(NOW.minus(Duration.ofHours(1))), true, 0,
                Optional.of(NOW.plus(Duration.ofMinutes(5))));
        var lockedRecently = leveled(4, Duration.ofMinutes(-1));
        var unlockedSoonest = leveled(5, Duration.ofMinutes(1));
        var unlockedLatest = leveled(1, Duration.ofDays(1));
        var expected = List.of(noLocksAt, lockedLongest, banned, lockedRecently, unlockedSoonest, unlockedLatest);

        assertEquals(expected, expected.reversed().stream().sorted(LearningOrder.URGENCY.comparator()).toList());
    }

    @Test void levelPutsLowestLevelFirstWithUrgencyBreakingTies() {
        var levelOneLocked = leveled(1, Duration.ofMinutes(-1));
        var levelOneUnlocked = leveled(1, Duration.ofMinutes(1));
        var levelTwoLocked = leveled(2, Duration.ofHours(-3));
        var expected = List.of(levelOneLocked, levelOneUnlocked, levelTwoLocked);

        assertEquals(expected, expected.reversed().stream().sorted(LearningOrder.LEVEL.comparator()).toList());
    }

    @Test void theSortButtonCyclesThroughBothOrders() {
        assertEquals(LearningOrder.LEVEL, LearningOrder.URGENCY.next());
        assertEquals(LearningOrder.URGENCY, LearningOrder.LEVEL.next());
    }
}
