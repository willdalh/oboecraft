package io.github.willdalh.oboecraft.core.settings;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The server settings. Rules that depend on a setting take this as a parameter.
 *
 * @param levels                     the settings for levels 1 and up, in order. Level 0 has no interval, and the top
 *                                   level, mastered, is one more than the number of levels here.
 * @param banLength                  how long a fail bans the item from being reviewed
 * @param percentageFactor           how many percentage points the removal rate drops for each level above 1
 * @param dropCountsAsInteraction    whether dropping a stack out of the inventory is an interaction, so a locked item
 *                                   can't be dropped
 * @param tsukumogamiAppearThreshold the overdue count the tsukumogami appears at, 0 to turn it off
 * @param tsukumogamiFadeThreshold   the overdue count it fades out at, below the appear threshold
 * @param tsukumogamiReviewFreeze    how long the tsukumogami stands still during one review, 0 so it never does
 */
public record Settings(
        List<LevelSettings> levels, Duration banLength, int percentageFactor, boolean dropCountsAsInteraction,
        int tsukumogamiAppearThreshold, int tsukumogamiFadeThreshold, Duration tsukumogamiReviewFreeze) {
    /** The most levels there can be. */
    public static final int MAX_LEVELS = 100;
    /** The longest interval or ban there can be, so the times worked out from them never overflow. */
    public static final Duration MAX_DURATION = Duration.ofDays(1_000_000);
    /** The highest percentage factor there can be. Above 50, the removal rate is 0 from level 2 anyway. */
    public static final int MAX_PERCENTAGE_FACTOR = 100;
    /** The highest tsukumogami appear threshold there can be. The fade threshold is always below it. */
    public static final int MAX_TSUKUMOGAMI_THRESHOLD = 1_000;
    /** The longest review freeze there can be, so the answer menu can't keep the tsukumogami still all night. */
    public static final Duration MAX_TSUKUMOGAMI_REVIEW_FREEZE = Duration.ofHours(1);

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
            10,
            5,
            Duration.ofSeconds(60));

    public Settings {
        levels = List.copyOf(levels);
        Objects.requireNonNull(banLength, "banLength");
        Objects.requireNonNull(tsukumogamiReviewFreeze, "tsukumogamiReviewFreeze");
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
        if (tsukumogamiAppearThreshold < 0 || tsukumogamiAppearThreshold > MAX_TSUKUMOGAMI_THRESHOLD) {
            throw new IllegalArgumentException("The tsukumogami appear threshold must be from 0 to "
                    + MAX_TSUKUMOGAMI_THRESHOLD + ", not " + tsukumogamiAppearThreshold);
        }
        if (tsukumogamiFadeThreshold < 0 || tsukumogamiFadeThreshold >= MAX_TSUKUMOGAMI_THRESHOLD) {
            throw new IllegalArgumentException("The tsukumogami fade threshold must be from 0 to "
                    + (MAX_TSUKUMOGAMI_THRESHOLD - 1) + ", not " + tsukumogamiFadeThreshold);
        }
        // With the tsukumogami turned off, the fade threshold is kept for when it is turned on again.
        if (tsukumogamiAppearThreshold > 0 && tsukumogamiFadeThreshold >= tsukumogamiAppearThreshold) {
            throw new IllegalArgumentException("The tsukumogami fade threshold must be below the appear threshold, "
                    + tsukumogamiAppearThreshold + ", not " + tsukumogamiFadeThreshold);
        }
        if (tsukumogamiReviewFreeze.isNegative()
                || tsukumogamiReviewFreeze.compareTo(MAX_TSUKUMOGAMI_REVIEW_FREEZE) > 0) {
            throw new IllegalArgumentException("The tsukumogami review freeze must be from 0 to "
                    + MAX_TSUKUMOGAMI_REVIEW_FREEZE.toHours() + " hour, not " + tsukumogamiReviewFreeze);
        }
    }

    /** These settings with the levels replaced. */
    public Settings withLevels(List<LevelSettings> levels) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, tsukumogamiAppearThreshold,
                tsukumogamiFadeThreshold, tsukumogamiReviewFreeze);
    }

    /** These settings with the ban length replaced. */
    public Settings withBanLength(Duration banLength) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, tsukumogamiAppearThreshold,
                tsukumogamiFadeThreshold, tsukumogamiReviewFreeze);
    }

    /** These settings with the percentage factor replaced. */
    public Settings withPercentageFactor(int percentageFactor) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, tsukumogamiAppearThreshold,
                tsukumogamiFadeThreshold, tsukumogamiReviewFreeze);
    }

    /** These settings with the drop setting replaced. */
    public Settings withDropCountsAsInteraction(boolean dropCountsAsInteraction) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, tsukumogamiAppearThreshold,
                tsukumogamiFadeThreshold, tsukumogamiReviewFreeze);
    }

    /** These settings with the tsukumogami's appear and fade thresholds replaced. */
    public Settings withTsukumogamiThresholds(int tsukumogamiAppearThreshold, int tsukumogamiFadeThreshold) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, tsukumogamiAppearThreshold,
                tsukumogamiFadeThreshold, tsukumogamiReviewFreeze);
    }

    /** These settings with the tsukumogami review freeze replaced. */
    public Settings withTsukumogamiReviewFreeze(Duration tsukumogamiReviewFreeze) {
        return new Settings(levels, banLength, percentageFactor, dropCountsAsInteraction, tsukumogamiAppearThreshold,
                tsukumogamiFadeThreshold, tsukumogamiReviewFreeze);
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
