package io.github.willdalh.oboecraft.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class LevelsTextTest {
    @Test
    void writesTheDefaultLevels() {
        assertEquals("10m, 1h, 4h, 1d, 3d, 7d, 14d, 30d", LevelsText.intervals(Settings.DEFAULTS.levels()));
        assertEquals("1, 1, 1, 1, 2, 2, 2, 2", LevelsText.demotions(Settings.DEFAULTS.levels()));
    }

    @Test
    void readsWrittenLevelsBackTheSame() {
        List<LevelSettings> levels = Settings.DEFAULTS.levels();

        assertEquals(levels, LevelsText.parse(LevelsText.intervals(levels), LevelsText.demotions(levels)));
    }

    @Test
    void readsLevelsWithAnySpacing() {
        assertEquals(List.of(new LevelSettings(Duration.ofMinutes(90), 1), new LevelSettings(Duration.ofDays(2), 0)),
                LevelsText.parse("1h 30m,2d ", " 1 ,0"));
    }

    @Test
    void rejectsInvalidLevels() {
        assertThrows(IllegalArgumentException.class, () -> LevelsText.parse("10m, 1h", "1"));
        assertThrows(IllegalArgumentException.class, () -> LevelsText.parse("10m, ", "1, 1"));
        assertThrows(IllegalArgumentException.class, () -> LevelsText.parse("10m", "one"));
        assertThrows(IllegalArgumentException.class, () -> LevelsText.parse("10m", "-1"));
        assertThrows(IllegalArgumentException.class, () -> LevelsText.parse("0s", "1"));
        assertThrows(IllegalArgumentException.class, () -> LevelsText.parse("", ""));
        assertThrows(IllegalArgumentException.class,
                () -> LevelsText.parse(String.join(",", java.util.Collections.nCopies(101, "1m")),
                        String.join(",", java.util.Collections.nCopies(101, "1"))));
    }
}
