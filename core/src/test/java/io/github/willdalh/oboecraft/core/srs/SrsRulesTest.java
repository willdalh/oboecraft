package io.github.willdalh.oboecraft.core.srs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.settings.LevelSettings;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SrsRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final Settings SETTINGS = Settings.DEFAULTS;

    private static SrsState.Leveled leveled(int level, Instant locksAt) {
        return new SrsState.Leveled(level, Optional.of(locksAt));
    }

    @Test
    void aNewItemIsLocked() {
        assertTrue(SrsRules.isLocked(SrsState.Leveled.NEW, NOW, SETTINGS));
    }

    @Test
    void levelZeroIsLockedEvenWithALocksAtTimeInTheFuture() {
        assertTrue(SrsRules.isLocked(leveled(0, NOW.plusSeconds(60)), NOW, SETTINGS));
    }

    @Test
    void anItemIsUnlockedBeforeItsLocksAtTime() {
        assertFalse(SrsRules.isLocked(leveled(1, NOW.plusMillis(1)), NOW, SETTINGS));
    }

    @Test
    void anItemIsLockedAtItsLocksAtTime() {
        assertTrue(SrsRules.isLocked(leveled(1, NOW), NOW, SETTINGS));
    }

    @Test
    void anItemIsLockedPastItsLocksAtTime() {
        assertTrue(SrsRules.isLocked(leveled(3, NOW.minusSeconds(1)), NOW, SETTINGS));
    }

    @Test
    void anItemBelowTheTopLevelWithNoLocksAtTimeIsLocked() {
        assertTrue(SrsRules.isLocked(new SrsState.Leveled(4, Optional.empty()), NOW, SETTINGS));
    }

    @Test
    void aMasteredItemIsNeverLocked() {
        assertFalse(SrsRules.isLocked(new SrsState.Leveled(9, Optional.empty()), NOW, SETTINGS));
    }

    @Test
    void aLevelAboveTheTopLevelCountsAsMastered() {
        Settings fewerLevels = new Settings(List.of(new LevelSettings(Duration.ofMinutes(10))), false);

        assertFalse(SrsRules.isLocked(leveled(5, NOW.minusSeconds(1)), NOW, fewerLevels));
    }

    @Test
    void anItemMasteredBeforeLevelsWereAddedIsLocked() {
        List<LevelSettings> nineLevels = new java.util.ArrayList<>(SETTINGS.levels());
        nineLevels.add(new LevelSettings(Duration.ofDays(60)));
        Settings moreLevels = new Settings(nineLevels, false);

        assertTrue(SrsRules.isLocked(new SrsState.Leveled(9, Optional.empty()), NOW, moreLevels));
    }

    @Test
    void aPassAtLevelZeroGivesLevelOneForTenMinutes() {
        SrsState passed = SrsRules.pass(SrsState.Leveled.NEW, NOW, SETTINGS);

        assertEquals(leveled(1, NOW.plus(Duration.ofMinutes(10))), passed);
        assertFalse(SrsRules.isLocked(passed, NOW.plus(Duration.ofMinutes(10)).minusMillis(1), SETTINGS));
        assertTrue(SrsRules.isLocked(passed, NOW.plus(Duration.ofMinutes(10)), SETTINGS));
    }

    @Test
    void aPassPromotesOneLevelWithThatLevelsInterval() {
        SrsState passed = SrsRules.pass(leveled(3, NOW.minusSeconds(5)), NOW, SETTINGS);

        assertEquals(leveled(4, NOW.plus(Duration.ofDays(1))), passed);
    }

    @Test
    void aPassBelowTheTopLevelMastersTheItem() {
        SrsState passed = SrsRules.pass(leveled(8, NOW), NOW, SETTINGS);

        assertEquals(new SrsState.Leveled(9, Optional.empty()), passed);
        assertFalse(SrsRules.isLocked(passed, NOW.plus(Duration.ofDays(3650)), SETTINGS));
    }

    @Test
    void aPassOfAnItemMasteredBeforeLevelsWereAddedReachesTheNewTopLevel() {
        List<LevelSettings> nineLevels = new java.util.ArrayList<>(SETTINGS.levels());
        nineLevels.add(new LevelSettings(Duration.ofDays(60)));
        Settings moreLevels = new Settings(nineLevels, false);

        assertEquals(new SrsState.Leveled(10, Optional.empty()),
                SrsRules.pass(new SrsState.Leveled(9, Optional.empty()), NOW, moreLevels));
    }

    @Test
    void aLevelCantBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new SrsState.Leveled(-1, Optional.empty()));
    }
}
