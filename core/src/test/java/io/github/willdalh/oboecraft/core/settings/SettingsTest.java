package io.github.willdalh.oboecraft.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsTest {
    @Test
    void theDefaultsHaveEightLevelsAndMasteredAtNine() {
        assertEquals(9, Settings.DEFAULTS.topLevel());
        assertEquals(Duration.ofMinutes(10), Settings.DEFAULTS.interval(1));
        assertEquals(Duration.ofHours(1), Settings.DEFAULTS.interval(2));
        assertEquals(Duration.ofHours(4), Settings.DEFAULTS.interval(3));
        assertEquals(Duration.ofDays(1), Settings.DEFAULTS.interval(4));
        assertEquals(Duration.ofDays(3), Settings.DEFAULTS.interval(5));
        assertEquals(Duration.ofDays(7), Settings.DEFAULTS.interval(6));
        assertEquals(Duration.ofDays(14), Settings.DEFAULTS.interval(7));
        assertEquals(Duration.ofDays(30), Settings.DEFAULTS.interval(8));
    }

    @Test
    void droppingIsntAnInteractionByDefault() {
        assertFalse(Settings.DEFAULTS.dropCountsAsInteraction());
    }

    @Test
    void onlyLevelsFromOneBelowTheTopHaveAnInterval() {
        assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.interval(0));
        assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.interval(9));
    }

    @Test
    void theTopLevelIsOneMoreThanTheNumberOfLevels() {
        Settings settings = Settings.DEFAULTS.withLevels(List.of(new LevelSettings(Duration.ofMinutes(1), 1)));

        assertEquals(2, settings.topLevel());
    }

    @Test
    void rejectsNoLevels() {
        assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.withLevels(List.of()));
    }

    @Test
    void rejectsAnIntervalThatIsntLongerThanZero() {
        assertThrows(IllegalArgumentException.class, () -> new LevelSettings(Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new LevelSettings(Duration.ofMinutes(-1), 1));
    }

    @Test
    void theDefaultsDemoteOneLevelUpToFourAndTwoFromFive() {
        for (int level = 1; level <= 4; level++) {
            assertEquals(1, Settings.DEFAULTS.demotion(level));
        }
        for (int level = 5; level <= 8; level++) {
            assertEquals(2, Settings.DEFAULTS.demotion(level));
        }
        assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.demotion(0));
        assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.demotion(9));
    }

    @Test
    void theDefaultBanIsFiveMinutesAndThePercentageFactorFive() {
        assertEquals(Duration.ofMinutes(5), Settings.DEFAULTS.banLength());
        assertEquals(5, Settings.DEFAULTS.percentageFactor());
    }

    @Test
    void rejectsANegativeDemotionBanLengthOrPercentageFactor() {
        assertThrows(IllegalArgumentException.class, () -> new LevelSettings(Duration.ofMinutes(1), -1));
        assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.withBanLength(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.withPercentageFactor(-1));
        assertEquals(Duration.ZERO, Settings.DEFAULTS.withBanLength(Duration.ZERO).banLength());
        assertEquals(0, new LevelSettings(Duration.ofMinutes(1), 0).demotion());
    }
}
