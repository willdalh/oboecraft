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
     * @param level         0 means never unlocked, and the top level means mastered
     * @param locksAt       when the item locks next. Empty at level 0 and when mastered.
     * @param demoted       set by a fail and cleared by the next pass, which then doesn't promote
     * @param wrongAttempts wrong attempts so far in the current review, 0 or {@link #MAX_WRONG_ATTEMPTS}
     * @param bannedUntil   when the latest ban ends. Empty if the item has never been banned.
     */
    record Leveled(int level, Optional<Instant> locksAt, boolean demoted, int wrongAttempts,
            Optional<Instant> bannedUntil) implements SrsState {
        /** The state of an item with no saved state: level 0 and locked. */
        public static final Leveled NEW = new Leveled(0, Optional.empty());

        /** A review has two attempts, so one wrong attempt can be kept between them. */
        public static final int MAX_WRONG_ATTEMPTS = 1;

        public Leveled {
            Objects.requireNonNull(locksAt, "locksAt");
            Objects.requireNonNull(bannedUntil, "bannedUntil");
            if (level < 0) {
                throw new IllegalArgumentException("A level can't be negative, not " + level);
            }
            if (wrongAttempts < 0 || wrongAttempts > MAX_WRONG_ATTEMPTS) {
                throw new IllegalArgumentException(
                        "Wrong attempts must be from 0 to " + MAX_WRONG_ATTEMPTS + ", not " + wrongAttempts);
            }
        }

        /** A state that was never demoted or banned and has no wrong attempt. */
        public Leveled(int level, Optional<Instant> locksAt) {
            this(level, locksAt, false, 0, Optional.empty());
        }

        /** Whether the item is banned at {@code now}: its latest ban ends after {@code now}. */
        public boolean isBannedAt(Instant now) {
            return bannedUntil.map(now::isBefore).orElse(false);
        }

        /** This state with its wrong attempts replaced. */
        public Leveled withWrongAttempts(int wrongAttempts) {
            return new Leveled(level, locksAt, demoted, wrongAttempts, bannedUntil);
        }
    }
}
