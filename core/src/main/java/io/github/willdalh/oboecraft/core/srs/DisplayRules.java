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
            case SrsState.Known known -> DisplayState.Normal.INSTANCE;
            case SrsState.Ignored ignored -> DisplayState.Normal.INSTANCE;
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

    /** The hot slot path constructs neither a display state nor a lock timer. */
    public static SlotMarker slotMarker(SrsState state, Instant now, Settings settings) {
        if (!(state instanceof SrsState.Leveled leveled)) {
            return SlotMarker.NONE;
        }
        if (leveled.level() == 0) {
            return SlotMarker.NEW;
        }
        if (leveled.level() >= settings.topLevel()) {
            return SlotMarker.NONE;
        }
        if (SrsRules.isLocked(leveled, now, settings)) {
            return SlotMarker.OVERDUE;
        }
        return LockTimer.closeToLocking(Duration.between(now, leveled.locksAt().orElseThrow()),
                settings.interval(leveled.level())) ? SlotMarker.NEAR_LOCK : SlotMarker.NONE;
    }
}
