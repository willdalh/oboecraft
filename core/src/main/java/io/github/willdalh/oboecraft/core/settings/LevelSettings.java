package io.github.willdalh.oboecraft.core.settings;

import java.time.Duration;
import java.util.Objects;

/**
 * The settings for one level.
 *
 * @param interval how long an item stays unlocked after a pass that leaves it at this level
 */
public record LevelSettings(Duration interval) {
    public LevelSettings {
        Objects.requireNonNull(interval, "interval");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("A level's interval must be longer than zero, not " + interval);
        }
    }
}
