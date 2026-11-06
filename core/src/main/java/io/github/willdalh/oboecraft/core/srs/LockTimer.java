package io.github.willdalh.oboecraft.core.srs;

import java.time.Duration;

/** The time left on an unlocked item's interval. Locked and mastered items have no timer. */
public record LockTimer(Duration remaining, Duration interval) {
    public LockTimer {
        if (remaining.isNegative() || remaining.isZero() || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("A lock timer needs positive remaining time and interval");
        }
    }

    /** Clamped when the server clock moves back or settings shorten an interval. */
    public double fractionRemaining() {
        return Math.min(1.0, seconds(remaining) / seconds(interval));
    }

    /** The final tenth of the interval, including its boundary. */
    public boolean closeToLocking() {
        return closeToLocking(remaining, interval);
    }

    static boolean closeToLocking(Duration remaining, Duration interval) {
        return remaining.compareTo(interval.dividedBy(10)) <= 0;
    }

    private static double seconds(Duration duration) {
        return duration.getSeconds() + duration.getNano() / 1_000_000_000.0;
    }
}
