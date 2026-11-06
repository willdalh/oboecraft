package io.github.willdalh.oboecraft.core.review;

/** How an answer in a review turned out. */
public enum ReviewOutcome {
    /** The answer was correct. The item is unlocked and the review ends. */
    PASS,
    /** The answer was wrong. The review goes on. */
    WRONG
}
