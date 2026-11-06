package io.github.willdalh.oboecraft.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationTextTest {
    @Test
    void readsEachUnitAndCombinations() {
        assertEquals(Duration.ofMinutes(10), DurationText.parse("10m"));
        assertEquals(Duration.ofDays(14), DurationText.parse("14d"));
        assertEquals(Duration.ofMinutes(90), DurationText.parse("1h 30m"));
        assertEquals(Duration.ofSeconds(93_784), DurationText.parse(" 1d  2h 3m 4s "));
        assertEquals(Duration.ofSeconds(90), DurationText.parse("90S"));
        assertEquals(Duration.ZERO, DurationText.parse("0s"));
    }

    @Test
    void rejectsTextThatIsntADuration() {
        for (String text : new String[]{"", " ", "10", "m", "10x", "1.5h", "-1m", "30m 1h", "1h 1h", "1h30m",
                "9999999999s"}) {
            assertThrows(IllegalArgumentException.class, () -> DurationText.parse(text), text);
        }
    }

    @Test
    void writesEveryUnitThatIsntZero() {
        assertEquals("10m", DurationText.format(Duration.ofMinutes(10)));
        assertEquals("1h 30m", DurationText.format(Duration.ofMinutes(90)));
        assertEquals("1d 2h 3m 4s", DurationText.format(Duration.ofSeconds(93_784)));
        assertEquals("0s", DurationText.format(Duration.ZERO));
        assertEquals("1s", DurationText.format(Duration.ofMillis(1999)));
        assertThrows(IllegalArgumentException.class, () -> DurationText.format(Duration.ofSeconds(-1)));
    }

    @Test
    void writtenDurationsReadBackTheSame() {
        for (var level : Settings.DEFAULTS.levels()) {
            assertEquals(level.interval(), DurationText.parse(DurationText.format(level.interval())));
        }
    }
}
