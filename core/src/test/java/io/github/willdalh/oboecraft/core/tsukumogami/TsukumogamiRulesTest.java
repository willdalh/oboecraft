package io.github.willdalh.oboecraft.core.tsukumogami;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TsukumogamiRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-09T22:00:00Z");
    private static final Settings SETTINGS = Settings.DEFAULTS;
    private static final long GAME_TICK = 3 * 24_000 + 18_000;
    private static final long EARLIER_GAME_TICK = 3 * 24_000 + 6_000;
    private static final Instant NOT_WAITING = NOW.minusSeconds(1);

    private static boolean shouldExist(long gameTime, OptionalLong killedAtTick, Instant waitsUntil) {
        return TsukumogamiRules.shouldExist(true, gameTime, killedAtTick, NOW, waitsUntil);
    }

    @Test
    void aPlayerBecomesHauntedAtTheAppearThreshold() {
        assertFalse(TsukumogamiRules.isHaunted(false, 9, SETTINGS));
        assertTrue(TsukumogamiRules.isHaunted(false, 10, SETTINGS));
        assertTrue(TsukumogamiRules.isHaunted(false, 40, SETTINGS));
    }

    @Test
    void aHauntedPlayerStaysHauntedUntilTheFadeThreshold() {
        assertTrue(TsukumogamiRules.isHaunted(true, 9, SETTINGS));
        assertTrue(TsukumogamiRules.isHaunted(true, 6, SETTINGS));
        assertFalse(TsukumogamiRules.isHaunted(true, 5, SETTINGS));
        assertFalse(TsukumogamiRules.isHaunted(true, 0, SETTINGS));
    }

    @Test
    void betweenTheThresholdsAPlayerWhoIsNotHauntedStaysSo() {
        assertFalse(TsukumogamiRules.isHaunted(false, 6, SETTINGS));
        assertFalse(TsukumogamiRules.isHaunted(false, 5, SETTINGS));
    }

    @Test
    void anAppearThresholdOfZeroTurnsItOff() {
        Settings off = SETTINGS.withTsukumogamiThresholds(0, 0);

        assertFalse(TsukumogamiRules.isOn(off));
        assertTrue(TsukumogamiRules.isOn(SETTINGS));
        assertFalse(TsukumogamiRules.isHaunted(false, 0, off));
        assertFalse(TsukumogamiRules.isHaunted(false, 40, off));
        assertFalse(TsukumogamiRules.isHaunted(true, 40, off));
    }

    @Test
    void itExistsOnlyWhileItsOwnerIsHaunted() {
        assertTrue(TsukumogamiRules.shouldExist(true, GAME_TICK, OptionalLong.empty(), NOW, NOT_WAITING));
        assertFalse(TsukumogamiRules.shouldExist(false, GAME_TICK, OptionalLong.empty(), NOW, NOT_WAITING));
    }

    @Test
    void itAppearsAtAnyGameTime() {
        assertTrue(shouldExist(EARLIER_GAME_TICK, OptionalLong.empty(), NOT_WAITING));
    }

    @Test
    void killReliefLastsExactlyHalfADay() {
        OptionalLong killedAtTick = OptionalLong.of(GAME_TICK);

        assertFalse(shouldExist(GAME_TICK, killedAtTick, NOT_WAITING));
        assertFalse(shouldExist(GAME_TICK + 11_999, killedAtTick, NOT_WAITING));
        assertTrue(shouldExist(GAME_TICK + 12_000, killedAtTick, NOT_WAITING));
        assertFalse(shouldExist(GAME_TICK + 12_000, killedAtTick, NOW.plusSeconds(1)));
    }

    @Test
    void itWaitsUntilItsDelayIsOver() {
        assertFalse(shouldExist(GAME_TICK, OptionalLong.empty(), NOW.plusMillis(1)));
        assertTrue(shouldExist(GAME_TICK, OptionalLong.empty(), NOW));
    }

    @Test
    void itsHealthGrowsWithTheOverdueCountUpToTheCap() {
        assertEquals(20, TsukumogamiRules.maxHealth(10, SETTINGS));
        assertEquals(26, TsukumogamiRules.maxHealth(11, SETTINGS));
        assertEquals(62, TsukumogamiRules.maxHealth(17, SETTINGS));
        assertEquals(98, TsukumogamiRules.maxHealth(23, SETTINGS));
        assertEquals(100, TsukumogamiRules.maxHealth(24, SETTINGS));
        assertEquals(100, TsukumogamiRules.maxHealth(Integer.MAX_VALUE, SETTINGS));
    }

    @Test
    void itsHealthCountsFromTheAppearThreshold() {
        Settings appearAt5 = SETTINGS.withTsukumogamiThresholds(5, 2);

        assertEquals(20, TsukumogamiRules.maxHealth(5, appearAt5));
        assertEquals(32, TsukumogamiRules.maxHealth(7, appearAt5));
        assertEquals(20, TsukumogamiRules.maxHealth(3, appearAt5));
    }

    @Test
    void aNewMaxHealthKeepsTheSameFraction() {
        assertEquals(10, TsukumogamiRules.scaledHealth(13, 26, 20), 1e-6);
        assertEquals(50, TsukumogamiRules.scaledHealth(10, 20, 100), 1e-6);
        assertEquals(20, TsukumogamiRules.scaledHealth(20, 20, 20), 1e-6);
        assertEquals(20, TsukumogamiRules.scaledHealth(0, 0, 20), 1e-6);
    }

    @Test
    void itStandsStillForTheFreezeOfAReviewThatIsGoing() {
        Optional<Instant> started = Optional.of(NOW.minusSeconds(59));

        assertTrue(TsukumogamiRules.standsStill(started, Optional.empty(), NOW, SETTINGS));
        assertTrue(TsukumogamiRules.standsStill(Optional.of(NOW.minusSeconds(60).plusMillis(1)), Optional.empty(),
                NOW, SETTINGS));
        assertFalse(TsukumogamiRules.standsStill(Optional.of(NOW.minusSeconds(60)), Optional.empty(), NOW, SETTINGS));
    }

    @Test
    void anOldReviewEndingDoesntMatterWhileANewOneIsGoing() {
        assertFalse(TsukumogamiRules.standsStill(
                Optional.of(NOW.minusSeconds(61)), Optional.of(NOW.minusSeconds(1)), NOW, SETTINGS));
    }

    @Test
    void itStandsStillForTwoSecondsAfterAReviewEnds() {
        assertTrue(TsukumogamiRules.standsStill(Optional.empty(), Optional.of(NOW.minusMillis(1_999)), NOW, SETTINGS));
        assertFalse(TsukumogamiRules.standsStill(Optional.empty(), Optional.of(NOW.minusSeconds(2)), NOW, SETTINGS));
        assertFalse(TsukumogamiRules.standsStill(Optional.empty(), Optional.empty(), NOW, SETTINGS));
    }

    @Test
    void attacksWaitForFiveContinuousSecondsWithoutThreats() {
        assertFalse(TsukumogamiRules.waitsForSafety(Optional.empty(), NOW));
        assertTrue(TsukumogamiRules.waitsForSafety(Optional.of(NOW), NOW));
        assertTrue(TsukumogamiRules.waitsForSafety(Optional.of(NOW.minusMillis(4_999)), NOW));
        assertFalse(TsukumogamiRules.waitsForSafety(Optional.of(NOW.minusSeconds(5)), NOW));
        assertTrue(TsukumogamiRules.waitsForSafety(Optional.of(NOW.minusSeconds(1)), NOW));
    }

    @Test
    void aFreezeOfZeroNeverStandsStill() {
        Settings noFreeze = SETTINGS.withTsukumogamiReviewFreeze(Duration.ZERO);

        assertFalse(TsukumogamiRules.standsStill(Optional.of(NOW), Optional.empty(), NOW, noFreeze));
        assertFalse(TsukumogamiRules.standsStill(Optional.empty(), Optional.of(NOW), NOW, noFreeze));
    }
}
