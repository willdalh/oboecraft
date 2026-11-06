package io.github.willdalh.oboecraft.core.settings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The levels setting as the settings screen shows it: the intervals as one list of {@link DurationText}s and the
 * demotions as one list of whole numbers, both separated by commas, like {@code 10m, 1h, 4h} and {@code 1, 1, 2}.
 */
public final class LevelsText {
    private static final String SEPARATOR = ", ";

    private LevelsText() {
    }

    /** The intervals, like {@code 10m, 1h, 4h}. */
    public static String intervals(List<LevelSettings> levels) {
        return levels.stream().map(level -> DurationText.format(level.interval()))
                .collect(Collectors.joining(SEPARATOR));
    }

    /** The demotions, like {@code 1, 1, 2}. */
    public static String demotions(List<LevelSettings> levels) {
        return levels.stream().map(level -> Integer.toString(level.demotion())).collect(Collectors.joining(SEPARATOR));
    }

    /**
     * Reads the levels from their intervals and demotions, one of each per level.
     *
     * @throws IllegalArgumentException if a value is invalid or the lists don't have the same length
     */
    public static List<LevelSettings> parse(String intervals, String demotions) {
        String[] intervalParts = intervals.split(",", -1);
        String[] demotionParts = demotions.split(",", -1);
        if (intervalParts.length > Settings.MAX_LEVELS) {
            throw new IllegalArgumentException("There can be at most " + Settings.MAX_LEVELS + " levels");
        }
        if (intervalParts.length != demotionParts.length) {
            throw new IllegalArgumentException("There must be one demotion for each interval, but there are "
                    + intervalParts.length + " intervals and " + demotionParts.length + " demotions");
        }
        List<LevelSettings> levels = new ArrayList<>();
        for (int index = 0; index < intervalParts.length; index++) {
            Duration interval = DurationText.parse(intervalParts[index]);
            int demotion;
            try {
                demotion = Integer.parseInt(demotionParts[index].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "\"" + demotionParts[index].trim() + "\" isn't a whole number of levels", e);
            }
            levels.add(new LevelSettings(interval, demotion));
        }
        return List.copyOf(levels);
    }
}
