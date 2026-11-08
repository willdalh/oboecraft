package io.github.willdalh.oboecraft.core.srs;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Instant;
import java.util.Optional;

/** When an item is locked, and how a pass or a fail changes its SRS state. */
public final class SrsRules {
    private SrsRules() {
    }

    /**
     * Whether the item is locked at {@code now}. A leveled item is locked at level 0, and below the top level when it
     * has no locks-at time or {@code now} is at or past it. A level at or above the top level counts as mastered and
     * is never locked.
     */
    public static boolean isLocked(SrsState state, Instant now, Settings settings) {
        return switch (state) {
            case SrsState.Known known -> false;
            case SrsState.Ignored ignored -> false;
            case SrsState.Leveled leveled -> leveled.level() == 0
                    || (leveled.level() < settings.topLevel()
                            && leveled.locksAt().map(locksAt -> !now.isBefore(locksAt)).orElse(true));
        };
    }

    /**
     * Whether the item is banned at {@code now}, so it can't be reviewed. A ban only matters while the item is locked,
     * so callers check {@link #isLocked} first. Only a fail bans an item, and it leaves the item locked until a pass,
     * which can't happen during the ban.
     */
    public static boolean isBanned(SrsState state, Instant now) {
        return state instanceof SrsState.Leveled leveled && leveled.isBannedAt(now);
    }

    /**
     * Whether the item is being learned, so it is on the word list's Learning tab: leveled from level 1 up to, but not
     * including, mastered. Since a fail never drops below level 1, an item joins on its first pass and stays until it
     * is mastered, marked known or ignored, or reverted.
     */
    public static boolean isLearning(SrsState state, Settings settings) {
        return state instanceof SrsState.Leveled leveled && leveled.level() >= 1 && leveled.level() < settings.topLevel();
    }

    /** Only these states appear in the word list's other tabs and can be reverted. */
    public static boolean canRevert(SrsState state, Settings settings) {
        return !(state instanceof SrsState.Leveled leveled) || leveled.level() >= settings.topLevel();
    }

    /**
     * The state after a pass at {@code now}. The item is promoted one level and stays unlocked for that level's
     * interval, or is mastered when it reaches the top level. A demoted item instead stays at its level, for that
     * level's interval, and is no longer demoted. Wrong attempts are cleared.
     */
    public static SrsState pass(SrsState state, Instant now, Settings settings) {
        return switch (state) {
            case SrsState.Known known -> known;
            case SrsState.Ignored ignored -> ignored;
            case SrsState.Leveled leveled -> {
                boolean staysAtLevel = leveled.demoted() && leveled.level() >= 1 && leveled.level() < settings.topLevel();
                int level = staysAtLevel ? leveled.level() : Math.min(leveled.level() + 1, settings.topLevel());
                Optional<Instant> locksAt = level == settings.topLevel()
                        ? Optional.empty()
                        : Optional.of(now.plus(settings.interval(level)));
                yield new SrsState.Leveled(level, locksAt, false, 0, leveled.bannedUntil());
            }
        };
    }

    /**
     * The state after a fail at {@code now}. The item drops by its level's demotion, but never below level 1, is
     * demoted, and is banned for the ban length. Wrong attempts are cleared. Its locks-at time is unchanged: it is in
     * the past, so the item stays locked.
     *
     * @throws IllegalArgumentException if the state isn't leveled from level 1 up to, but not including, the top
     *                                  level. Level 0 is a lesson, which can't fail, and a mastered item is never
     *                                  reviewed.
     */
    public static SrsState.Leveled fail(SrsState state, Instant now, Settings settings) {
        if (!(state instanceof SrsState.Leveled leveled) || leveled.level() < 1
                || leveled.level() >= settings.topLevel()) {
            throw new IllegalArgumentException("Only a leveled item below the top level can fail, not " + state);
        }
        int level = Math.max(1, leveled.level() - settings.demotion(leveled.level()));
        return new SrsState.Leveled(level, leveled.locksAt(), true, 0, Optional.of(now.plus(settings.banLength())));
    }
}
