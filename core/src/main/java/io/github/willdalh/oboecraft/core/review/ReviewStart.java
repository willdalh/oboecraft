package io.github.willdalh.oboecraft.core.review;

import java.time.Instant;
import java.util.Objects;

/** Whether a review can start for an item. */
public sealed interface ReviewStart {
    /**
     * The review starts.
     *
     * @param level        the item's level, which the answer menu shows. Level 0 is a lesson.
     * @param attemptsLeft how many answers the review has before a fail. A lesson never fails, so it doesn't count
     *                     its attempts.
     */
    record Started(int level, int attemptsLeft) implements ReviewStart {
    }

    /** The item isn't locked, so there is nothing to review. */
    record NotLocked() implements ReviewStart {
    }

    /**
     * The item is locked, but banned after a fail, so it can't be reviewed yet.
     *
     * @param until when the ban ends
     */
    record Banned(Instant until) implements ReviewStart {
        public Banned {
            Objects.requireNonNull(until, "until");
        }
    }
}
