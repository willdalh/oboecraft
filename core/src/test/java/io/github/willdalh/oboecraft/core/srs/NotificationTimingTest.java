package io.github.willdalh.oboecraft.core.srs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NotificationTimingTest {
    @Test
    void theNotificationIsFullyOpaqueUntilTheFadeStarts() {
        assertEquals(1f, NotificationTiming.opacity(Duration.ZERO));
        assertEquals(1f, NotificationTiming.opacity(Duration.ofMillis(1999)));
        assertEquals(1f, NotificationTiming.opacity(Duration.ofSeconds(2)));
    }

    @Test
    void itFadesOutLinearlyInTheLastSecond() {
        assertEquals(0.5f, NotificationTiming.opacity(Duration.ofMillis(2500)), 1e-6);
        assertEquals(0.25f, NotificationTiming.opacity(Duration.ofMillis(2750)), 1e-6);
    }

    @Test
    void itIsOverAfterThreeSeconds() {
        assertFalse(NotificationTiming.isOver(Duration.ofMillis(2999)));
        assertTrue(NotificationTiming.isOver(Duration.ofSeconds(3)));
        assertEquals(0f, NotificationTiming.opacity(Duration.ofSeconds(3)));
        assertEquals(0f, NotificationTiming.opacity(Duration.ofSeconds(10)));
    }
}
