package io.github.willdalh.oboecraft.core.review;

import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.util.Objects;

/**
 * The result of an answer or an action in a review.
 *
 * @param outcome     how the review turned out
 * @param newState    the item's SRS state afterwards
 * @param levelBefore the item's level before the review, which a fail's punishment uses
 */
public record ReviewResult(ReviewOutcome outcome, SrsState newState, int levelBefore) {
    public ReviewResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(newState, "newState");
    }
}
