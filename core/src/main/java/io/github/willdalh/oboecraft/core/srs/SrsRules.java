package io.github.willdalh.oboecraft.core.srs;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Instant;
import java.util.Optional;

/** When an item is locked, and how a pass changes its SRS state. */
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

    /** Only these states appear in the word list and can be reverted. */
    public static boolean canRevert(SrsState state, Settings settings) {
        return !(state instanceof SrsState.Leveled leveled) || leveled.level() >= settings.topLevel();
    }

    /**
     * The state after a pass at {@code now}: the item is promoted one level and stays unlocked for that level's
     * interval, or is mastered when it reaches the top level.
     */
    public static SrsState pass(SrsState state, Instant now, Settings settings) {
        return switch (state) {
            case SrsState.Known known -> known;
            case SrsState.Ignored ignored -> ignored;
            case SrsState.Leveled leveled -> {
                int level = Math.min(leveled.level() + 1, settings.topLevel());
                Optional<Instant> locksAt = level == settings.topLevel()
                        ? Optional.empty()
                        : Optional.of(now.plus(settings.interval(level)));
                yield new SrsState.Leveled(level, locksAt);
            }
        };
    }
}
