package io.github.willdalh.oboecraft.core.review;

import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.util.Objects;

/**
 * The result of an answer in a review.
 *
 * @param outcome  how the answer turned out
 * @param newState the item's SRS state after the answer
 */
public record ReviewResult(ReviewOutcome outcome, SrsState newState) {
    public ReviewResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(newState, "newState");
    }
}
