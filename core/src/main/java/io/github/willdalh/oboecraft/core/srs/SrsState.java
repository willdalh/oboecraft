package io.github.willdalh.oboecraft.core.srs;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything tracked about one item for one player.
 */
public sealed interface SrsState {
    /** Already known, unlocked forever. */
    record Known() implements SrsState {}

    /** Left out of learning and blocked from use. */
    record Ignored() implements SrsState {}

    /**
     * An item that moves through the levels, from 0 up to mastered.
     *
     * @param level    0 means never unlocked, and the top level means mastered
     * @param locksAt  when the item locks next. Empty at level 0 and when mastered.
     */
    record Leveled(int level, Optional<Instant> locksAt) implements SrsState {
        /** The state of an item with no saved state: level 0 and locked. */
        public static final Leveled NEW = new Leveled(0, Optional.empty());

        public Leveled {
            Objects.requireNonNull(locksAt, "locksAt");
            if (level < 0) {
                throw new IllegalArgumentException("A level can't be negative, not " + level);
            }
        }
    }
}
