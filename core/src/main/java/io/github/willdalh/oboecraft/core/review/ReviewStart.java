package io.github.willdalh.oboecraft.core.review;

/** Whether a review can start for an item. */
public sealed interface ReviewStart {
    /**
     * The review starts.
     *
     * @param level the item's level, which the answer menu shows
     */
    record Started(int level) implements ReviewStart {
    }

    /** The item isn't locked, so there is nothing to review. */
    record NotLocked() implements ReviewStart {
    }
}
