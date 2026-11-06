package io.github.willdalh.oboecraft.core.srs;

import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** The time left on an unlocked item's interval. Locked and mastered items have no timer. */
public record LockTimer(Duration remaining, Duration interval) {
    public LockTimer {
        if (remaining.isNegative() || remaining.isZero() || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("A lock timer needs positive remaining time and interval");
        }
    }

    public static Optional<LockTimer> of(SrsState state, Instant now, Settings settings) {
        if (SrsRules.isLocked(state, now, settings)) {
            return Optional.empty();
        }
        return switch (state) {
            case SrsState.Leveled leveled -> leveled.level() >= settings.topLevel()
                    ? Optional.empty()
                    : leveled.locksAt().map(locksAt -> new LockTimer(
                            Duration.between(now, locksAt), settings.interval(leveled.level())));
        };
    }

    /** Clamped when the server clock moves back or settings shorten an interval. */
    public double fractionRemaining() {
        return Math.min(1.0, seconds(remaining) / seconds(interval));
    }

    /** The final tenth of the interval, including its boundary. */
    public boolean closeToLocking() {
        return remaining.compareTo(interval.dividedBy(10)) <= 0;
    }

    private static double seconds(Duration duration) {
        return duration.getSeconds() + duration.getNano() / 1_000_000_000.0;
    }
}
