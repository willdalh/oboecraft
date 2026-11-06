package io.github.willdalh.oboecraft.core.srs;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;

/** Display rules, independent of whether an interaction is allowed. */
public final class DisplayRules {
    private DisplayRules() {
    }

    public static DisplayState of(SrsState state, Instant now, Settings settings) {
        return switch (state) {
            case SrsState.Leveled leveled -> {
                if (SrsRules.isLocked(leveled, now, settings)) {
                    yield DisplayState.Hidden.INSTANCE;
                }
                if (leveled.level() >= settings.topLevel()) {
                    yield DisplayState.Normal.INSTANCE;
                }
                yield new DisplayState.Timed(new LockTimer(
                        Duration.between(now, leveled.locksAt().orElseThrow()), settings.interval(leveled.level())));
            }
        };
    }

    /** The slot marker needs only this boolean, without constructing a display state or lock timer. */
    public static boolean closeToLocking(SrsState state, Instant now, Settings settings) {
        if (!(state instanceof SrsState.Leveled leveled) || leveled.level() >= settings.topLevel()
                || SrsRules.isLocked(leveled, now, settings)) {
            return false;
        }
        return LockTimer.closeToLocking(Duration.between(now, leveled.locksAt().orElseThrow()),
                settings.interval(leveled.level()));
    }
}
