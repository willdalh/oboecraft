package io.github.willdalh.oboecraft.core.review;

/** How an answer or an action in a review turned out. */
public enum ReviewOutcome {
    /** The answer was correct. The item is unlocked and the review ends. */
    PASS,
    /** The first answer was wrong. The review goes on with one attempt left. */
    WRONG,
    /** The lesson continues with its answer visible. */
    LESSON,
    /** The second answer was wrong, or the player chose Don't know. The item is punished and the review ends. */
    FAIL
}
