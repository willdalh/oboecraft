package io.github.willdalh.oboecraft.core.settings;

import java.time.Duration;
import java.util.Objects;

/**
 * The settings for one level.
 *
 * @param interval how long an item stays unlocked after a pass that leaves it at this level
 * @param demotion how many levels a fail at this level drops the item, though never below level 1
 */
public record LevelSettings(Duration interval, int demotion) {
    public LevelSettings {
        Objects.requireNonNull(interval, "interval");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("A level's interval must be longer than zero, not " + interval);
        }
        if (interval.compareTo(Settings.MAX_DURATION) > 0) {
            throw new IllegalArgumentException("A level's interval can be at most " + Settings.MAX_DURATION.toDays()
                    + " days, not " + interval);
        }
        if (demotion < 0 || demotion > Settings.MAX_LEVELS) {
            throw new IllegalArgumentException(
                    "A level's demotion must be from 0 to " + Settings.MAX_LEVELS + ", not " + demotion);
        }
    }
}
