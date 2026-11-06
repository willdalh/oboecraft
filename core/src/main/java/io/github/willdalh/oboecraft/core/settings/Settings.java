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
 */
public record Settings(
        List<LevelSettings> levels, Duration banLength, int percentageFactor, boolean dropCountsAsInteraction) {
    /** The default settings, which are the only ones until settings files are added. */
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
            false);

    public Settings {
        levels = List.copyOf(levels);
        Objects.requireNonNull(banLength, "banLength");
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one level");
        }
        if (banLength.isNegative()) {
            throw new IllegalArgumentException("The ban length can't be negative, not " + banLength);
        }
        if (percentageFactor < 0) {
            throw new IllegalArgumentException("The percentage factor can't be negative, not " + percentageFactor);
        }
    }

    /** These settings with the levels replaced. */
    public Settings withLevels(List<LevelSettings> levels) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction);
    }

    /** These settings with the ban length replaced. */
    public Settings withBanLength(Duration banLength) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction);
    }

    /** These settings with the percentage factor replaced. */
    public Settings withPercentageFactor(int percentageFactor) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction);
    }

    /** These settings with the drop setting replaced. */
    public Settings withDropCountsAsInteraction(boolean dropCountsAsInteraction) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction);
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
