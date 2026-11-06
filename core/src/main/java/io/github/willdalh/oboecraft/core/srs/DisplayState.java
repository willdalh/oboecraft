package io.github.willdalh.oboecraft.core.srs;

import java.util.Objects;

/** The mutually exclusive ways to display an item's SRS state. */
public sealed interface DisplayState {
    enum Hidden implements DisplayState { INSTANCE }

    enum Normal implements DisplayState { INSTANCE }

    record Timed(LockTimer timer) implements DisplayState {
        public Timed {
            Objects.requireNonNull(timer, "timer");
        }
    }
}
