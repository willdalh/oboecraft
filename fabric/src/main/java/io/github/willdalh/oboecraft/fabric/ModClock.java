package io.github.willdalh.oboecraft.fabric;

import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The one clock that all game code reads the current time from. It is the system clock in the game, and game tests
 * swap in a clock they control. Times are to the millisecond, as they are saved and sent.
 */
public final class ModClock implements InstantSource {
    private volatile InstantSource source = InstantSource.system();

    @Override
    public Instant instant() {
        return source.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Reads the time from {@code source} from now on. For game tests. */
    public void setSource(InstantSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }
}
