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
    private static final long NIGHT = 3 * Night.DAY_LENGTH + 18_000;
    private static final long DAY = 3 * Night.DAY_LENGTH + 6_000;
    private static final Instant NOT_WAITING = NOW.minusSeconds(1);

    private static boolean shouldExist(int overdueCount, long dayTime, OptionalLong killedOnNight, Instant waitsUntil,
            Settings settings) {
        return TsukumogamiRules.shouldExist(overdueCount, dayTime, killedOnNight, NOW, waitsUntil, settings);
    }

    @Test
    void itAppearsAtNightFromTheThreshold() {
        assertFalse(shouldExist(4, NIGHT, OptionalLong.empty(), NOT_WAITING, SETTINGS));
        assertTrue(shouldExist(5, NIGHT, OptionalLong.empty(), NOT_WAITING, SETTINGS));
        assertTrue(shouldExist(40, NIGHT, OptionalLong.empty(), NOT_WAITING, SETTINGS));
    }

    @Test
    void itNeverAppearsDuringTheDay() {
        assertFalse(shouldExist(40, DAY, OptionalLong.empty(), NOT_WAITING, SETTINGS));
    }

    @Test
    void aThresholdOfZeroTurnsItOff() {
        Settings off = SETTINGS.withTsukumogamiThreshold(0);

        assertFalse(TsukumogamiRules.isOn(off));
        assertTrue(TsukumogamiRules.isOn(SETTINGS));
        assertFalse(shouldExist(0, NIGHT, OptionalLong.empty(), NOT_WAITING, off));
        assertFalse(shouldExist(40, NIGHT, OptionalLong.empty(), NOT_WAITING, off));
    }

    @Test
    void killedItIsGoneForTheRestOfThatNightOnly() {
        OptionalLong killedTonight = OptionalLong.of(Night.nightOf(NIGHT));

        assertFalse(shouldExist(10, NIGHT, killedTonight, NOT_WAITING, SETTINGS));
        assertFalse(shouldExist(10, NIGHT + 5_000, killedTonight, NOT_WAITING, SETTINGS));
        assertTrue(shouldExist(10, NIGHT + Night.DAY_LENGTH, killedTonight, NOT_WAITING, SETTINGS));
    }

    @Test
    void itWaitsUntilItsDelayIsOver() {
        assertFalse(shouldExist(10, NIGHT, OptionalLong.empty(), NOW.plusMillis(1), SETTINGS));
        assertTrue(shouldExist(10, NIGHT, OptionalLong.empty(), NOW, SETTINGS));
    }

    @Test
    void itsHealthGrowsWithTheOverdueCountUpToTheCap() {
        assertEquals(20, TsukumogamiRules.maxHealth(5, SETTINGS));
        assertEquals(26, TsukumogamiRules.maxHealth(6, SETTINGS));
        assertEquals(62, TsukumogamiRules.maxHealth(12, SETTINGS));
        assertEquals(98, TsukumogamiRules.maxHealth(18, SETTINGS));
        assertEquals(100, TsukumogamiRules.maxHealth(19, SETTINGS));
        assertEquals(100, TsukumogamiRules.maxHealth(Integer.MAX_VALUE, SETTINGS));
    }

    @Test
    void itsHealthCountsFromTheThreshold() {
        Settings threshold10 = SETTINGS.withTsukumogamiThreshold(10);

        assertEquals(20, TsukumogamiRules.maxHealth(10, threshold10));
        assertEquals(32, TsukumogamiRules.maxHealth(12, threshold10));
        assertEquals(20, TsukumogamiRules.maxHealth(3, threshold10));
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
    void aFreezeOfZeroNeverStandsStill() {
        Settings noFreeze = SETTINGS.withTsukumogamiReviewFreeze(Duration.ZERO);

        assertFalse(TsukumogamiRules.standsStill(Optional.of(NOW), Optional.empty(), NOW, noFreeze));
        assertFalse(TsukumogamiRules.standsStill(Optional.empty(), Optional.of(NOW), NOW, noFreeze));
    }
}
