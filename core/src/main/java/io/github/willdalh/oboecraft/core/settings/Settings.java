package io.github.willdalh.oboecraft.core.settings;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The server settings. Rules that depend on a setting take this as a parameter.
 *
 * @param levels                  the settings for levels 1 and up, in order. Level 0 has no interval, and the top
 *                                level, mastered, is one more than the number of levels here.
 * @param banLength               how long a fail bans the item from being reviewed
 * @param percentageFactor        how many percentage points the removal rate drops for each level above 1
 * @param dropCountsAsInteraction whether dropping a stack out of the inventory is an interaction, so a locked item
 *                                can't be dropped
 * @param useOwnProgress          whether players bring their own progress, which their client keeps, instead of the
 *                                server keeping each player's progress
 */
public record Settings(
        List<LevelSettings> levels, Duration banLength, int percentageFactor, boolean dropCountsAsInteraction,
        boolean useOwnProgress) {
    /** The most levels there can be. */
    public static final int MAX_LEVELS = 100;
    /** The longest interval or ban there can be, so the times worked out from them never overflow. */
    public static final Duration MAX_DURATION = Duration.ofDays(1_000_000);
    /** The highest percentage factor there can be. Above 50, the removal rate is 0 from level 2 anyway. */
    public static final int MAX_PERCENTAGE_FACTOR = 100;

    /** The default settings, used when no settings file says otherwise. */
    public static final Settings DEFAULTS = new Settings(List.of(
            new LevelSettings(Duration.ofMinutes(10), 1),
            new LevelSettings(Duration.ofHours(1), 1),
            new LevelSettings(Duration.ofHours(4), 1),
            new LevelSettings(Duration.ofDays(1), 1),
            new LevelSettings(Duration.ofDays(3), 2),
            new LevelSettings(Duration.ofDays(7), 2),
            new LevelSettings(Duration.ofDays(14), 2),
            new LevelSettings(Duration.ofDays(30), 2)),
            Duration.ofMinutes(5),
            5,
            false,
            true);

    public Settings {
        levels = List.copyOf(levels);
        Objects.requireNonNull(banLength, "banLength");
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one level");
        }
        if (levels.size() > MAX_LEVELS) {
            throw new IllegalArgumentException("There can be at most " + MAX_LEVELS + " levels, not " + levels.size());
        }
        if (banLength.isNegative() || banLength.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException(
                    "The ban length must be from 0 to " + MAX_DURATION.toDays() + " days, not " + banLength);
        }
        if (percentageFactor < 0 || percentageFactor > MAX_PERCENTAGE_FACTOR) {
            throw new IllegalArgumentException("The percentage factor must be from 0 to " + MAX_PERCENTAGE_FACTOR
                    + ", not " + percentageFactor);
        }
    }

    /** These settings with the levels replaced. */
    public Settings withLevels(List<LevelSettings> levels) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, useOwnProgress);
    }

    /** These settings with the ban length replaced. */
    public Settings withBanLength(Duration banLength) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, useOwnProgress);
    }

    /** These settings with the percentage factor replaced. */
    public Settings withPercentageFactor(int percentageFactor) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, useOwnProgress);
    }

    /** These settings with the drop setting replaced. */
    public Settings withDropCountsAsInteraction(boolean dropCountsAsInteraction) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, useOwnProgress);
    }

    /** These settings with the own progress setting replaced. */
    public Settings withUseOwnProgress(boolean useOwnProgress) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, useOwnProgress);
    }

    /** The top level, mastered. */
    public int topLevel() {
        return levels.size() + 1;
    }

    /**
     * The interval of a level from 1 up to, but not including, the top level.
     *
     * @throws IllegalArgumentException for level 0, the top level and above
     */
    public Duration interval(int level) {
        return levelSettings(level).interval();
    }

    /**
     * How many levels a fail at a level from 1 up to, but not including, the top level drops the item.
     *
     * @throws IllegalArgumentException for level 0, the top level and above
     */
    public int demotion(int level) {
        return levelSettings(level).demotion();
    }

    private LevelSettings levelSettings(int level) {
        if (level < 1 || level >= topLevel()) {
            throw new IllegalArgumentException("Level " + level + " has no settings");
        }
        return levels.get(level - 1);
    }
}
