package io.github.willdalh.oboecraft.core.tsukumogami;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NightTest {
    @Test
    void nightIsFromDuskUntilDawn() {
        assertFalse(Night.isNight(0));
        assertFalse(Night.isNight(6_000));
        assertFalse(Night.isNight(12_541));
        assertTrue(Night.isNight(12_542));
        assertTrue(Night.isNight(18_000));
        assertTrue(Night.isNight(23_459));
        assertFalse(Night.isNight(23_460));
    }

    @Test
    void everyDayHasItsOwnNight() {
        assertTrue(Night.isNight(5 * Night.DAY_LENGTH + 18_000));
        assertFalse(Night.isNight(5 * Night.DAY_LENGTH + 1_000));
        assertEquals(0, Night.nightOf(18_000));
        assertEquals(5, Night.nightOf(5 * Night.DAY_LENGTH + 13_000));
        assertEquals(5, Night.nightOf(5 * Night.DAY_LENGTH + 23_000));
    }

    @Test
    void aNegativeDayTimeIsStillATimeOfDay() {
        assertTrue(Night.isNight(18_000 - Night.DAY_LENGTH));
        assertEquals(-1, Night.nightOf(18_000 - Night.DAY_LENGTH));
    }
}
