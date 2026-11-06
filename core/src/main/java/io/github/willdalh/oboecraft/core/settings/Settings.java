package io.github.willdalh.oboecraft.core.settings;

import java.time.Duration;
import java.util.List;

/**
 * The server settings. Rules that depend on a setting take this as a parameter.
 *
 * @param levels                  the settings for levels 1 and up, in order. Level 0 has no interval, and the top
 *                                level, mastered, is one more than the number of levels here.
 * @param dropCountsAsInteraction whether dropping a stack out of the inventory is an interaction, so a locked item
 *                                can't be dropped
 */
public record Settings(List<LevelSettings> levels, boolean dropCountsAsInteraction) {
    /** The default settings, which are the only ones until settings files are added. */
    public static final Settings DEFAULTS = new Settings(List.of(
            new LevelSettings(Duration.ofMinutes(10)),
            new LevelSettings(Duration.ofHours(1)),
            new LevelSettings(Duration.ofHours(4)),
            new LevelSettings(Duration.ofDays(1)),
            new LevelSettings(Duration.ofDays(3)),
            new LevelSettings(Duration.ofDays(7)),
            new LevelSettings(Duration.ofDays(14)),
            new LevelSettings(Duration.ofDays(30))),
            false);

    public Settings {
        levels = List.copyOf(levels);
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one level");
        }
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
        if (level < 1 || level >= topLevel()) {
            throw new IllegalArgumentException("Level " + level + " has no interval");
        }
        return levels.get(level - 1).interval();
    }
}
