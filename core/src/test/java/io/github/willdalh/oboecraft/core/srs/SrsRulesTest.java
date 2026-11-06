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
        Settings fewerLevels = Settings.DEFAULTS.withLevels(List.of(new LevelSettings(Duration.ofMinutes(10), 1)));

        assertFalse(SrsRules.isLocked(leveled(5, NOW.minusSeconds(1)), NOW, fewerLevels));
    }

    @Test
    void anItemMasteredBeforeLevelsWereAddedIsLocked() {
        List<LevelSettings> nineLevels = new java.util.ArrayList<>(SETTINGS.levels());
        nineLevels.add(new LevelSettings(Duration.ofDays(60), 1));
        Settings moreLevels = Settings.DEFAULTS.withLevels(nineLevels);

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
        nineLevels.add(new LevelSettings(Duration.ofDays(60), 1));
        Settings moreLevels = Settings.DEFAULTS.withLevels(nineLevels);

        assertEquals(new SrsState.Leveled(10, Optional.empty()),
                SrsRules.pass(new SrsState.Leveled(9, Optional.empty()), NOW, moreLevels));
    }

    @Test
    void aLevelCantBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new SrsState.Leveled(-1, Optional.empty()));
    }

    @Test
    void wrongAttemptsAreFromZeroToOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new SrsState.Leveled(1, Optional.empty(), false, -1, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new SrsState.Leveled(1, Optional.empty(), false, 2, Optional.empty()));
    }

    @Test
    void aFailDemotesOneLevelBelowLevelFiveAndBansTheItem() {
        SrsState.Leveled failed = SrsRules.fail(
                new SrsState.Leveled(3, Optional.of(NOW.minusSeconds(5)), false, 1, Optional.empty()), NOW, SETTINGS);

        assertEquals(new SrsState.Leveled(
                2, Optional.of(NOW.minusSeconds(5)), true, 0, Optional.of(NOW.plus(Duration.ofMinutes(5)))), failed);
        assertTrue(SrsRules.isLocked(failed, NOW, SETTINGS));
        assertTrue(SrsRules.isBanned(failed, NOW.plus(Duration.ofMinutes(5)).minusMillis(1)));
        assertFalse(SrsRules.isBanned(failed, NOW.plus(Duration.ofMinutes(5))));
    }

    @Test
    void aFailDemotesTwoLevelsFromLevelFive() {
        assertEquals(3, SrsRules.fail(leveled(5, NOW), NOW, SETTINGS).level());
        assertEquals(6, SrsRules.fail(leveled(8, NOW), NOW, SETTINGS).level());
    }

    @Test
    void aFailNeverDemotesBelowLevelOne() {
        assertEquals(1, SrsRules.fail(leveled(1, NOW), NOW, SETTINGS).level());
        Settings bigDemotion = SETTINGS.withLevels(List.of(
                new LevelSettings(Duration.ofMinutes(10), 1), new LevelSettings(Duration.ofHours(1), 5)));
        assertEquals(1, SrsRules.fail(leveled(2, NOW), NOW, bigDemotion).level());
    }

    @Test
    void aZeroDemotionKeepsTheLevelButStillDemotes() {
        Settings noDemotion = SETTINGS.withLevels(List.of(
                new LevelSettings(Duration.ofMinutes(10), 0), new LevelSettings(Duration.ofHours(1), 0)));

        SrsState.Leveled failed = SrsRules.fail(leveled(2, NOW), NOW, noDemotion);

        assertEquals(2, failed.level());
        assertTrue(failed.demoted());
    }

    @Test
    void aZeroBanLengthBansNothing() {
        SrsState.Leveled failed = SrsRules.fail(leveled(2, NOW), NOW, SETTINGS.withBanLength(Duration.ZERO));

        assertFalse(SrsRules.isBanned(failed, NOW));
    }

    @Test
    void onlyALeveledItemFromLevelOneBelowTheTopCanFail() {
        assertThrows(IllegalArgumentException.class, () -> SrsRules.fail(SrsState.Leveled.NEW, NOW, SETTINGS));
        assertThrows(IllegalArgumentException.class,
                () -> SrsRules.fail(new SrsState.Leveled(9, Optional.empty()), NOW, SETTINGS));
        assertThrows(IllegalArgumentException.class, () -> SrsRules.fail(new SrsState.Known(), NOW, SETTINGS));
    }

    @Test
    void aPassAfterAFailUnlocksAtTheNewLevelWithoutPromoting() {
        SrsState.Leveled failed = SrsRules.fail(leveled(5, NOW), NOW, SETTINGS);
        Instant later = NOW.plus(Duration.ofMinutes(10));

        SrsState passed = SrsRules.pass(failed, later, SETTINGS);

        assertEquals(new SrsState.Leveled(3, Optional.of(later.plus(Duration.ofHours(4))), false, 0,
                failed.bannedUntil()), passed);
        SrsState passedAgain = SrsRules.pass(passed, later.plus(Duration.ofDays(1)), SETTINGS);
        assertEquals(4, ((SrsState.Leveled) passedAgain).level());
    }

    @Test
    void aPassClearsWrongAttempts() {
        SrsState passed = SrsRules.pass(
                new SrsState.Leveled(2, Optional.of(NOW), false, 1, Optional.empty()), NOW, SETTINGS);

        assertEquals(0, ((SrsState.Leveled) passed).wrongAttempts());
    }

    @Test
    void anItemThatWasNeverFailedIsntBanned() {
        assertFalse(SrsRules.isBanned(SrsState.Leveled.NEW, NOW));
        assertFalse(SrsRules.isBanned(new SrsState.Known(), NOW));
    }
}
