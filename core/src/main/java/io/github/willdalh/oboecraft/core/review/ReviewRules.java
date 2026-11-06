package io.github.willdalh.oboecraft.core.review;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.time.Instant;

/** The rules of a review: when one can start, and what an answer does. */
public final class ReviewRules {
    private ReviewRules() {
    }

    /**
     * Whether a review of the item can start. It can only start for a locked item, so reviewing early isn't possible.
     * Uses {@link SrsRules#isLocked}, so it agrees with the lock check.
     */
    public static ReviewStart start(SrsState state, Instant now, Settings settings) {
        if (!SrsRules.isLocked(state, now, settings)) {
            return new ReviewStart.NotLocked();
        }
        return switch (state) {
            case SrsState.Leveled leveled -> new ReviewStart.Started(leveled.level());
            case SrsState.Known known -> new ReviewStart.NotLocked();
            case SrsState.Ignored ignored -> new ReviewStart.NotLocked();
        };
    }

    public static boolean isLesson(SrsState state) {
        return state instanceof SrsState.Leveled leveled && leveled.level() == 0;
    }

    /**
     * Whether the typed text counts as an attempt. Text with no letter or digit doesn't, like an empty typing bar or
     * a stray {@code 。}, since answer checking ignores everything else: pressing Enter with it does nothing.
     */
    public static boolean isAttempt(String typed) {
        return !JapaneseText.normalize(typed).isEmpty();
    }

    /**
     * The result of an answer in a review of a locked item. A correct answer is a pass. A wrong answer leaves the
     * state as it is.
     *
     * @throws IllegalArgumentException if the typed text isn't an attempt, see {@link #isAttempt}
     */
    public static ReviewResult answer(
            NameListEntry entry, SrsState state, String typed, Instant now, Settings settings) {
        if (!isAttempt(typed)) {
            throw new IllegalArgumentException("An answer with no letter or digit isn't an attempt");
        }
        if (AnswerChecker.isCorrect(entry, typed)) {
            return new ReviewResult(ReviewOutcome.PASS, SrsRules.pass(state, now, settings));
        }
        return new ReviewResult(isLesson(state) ? ReviewOutcome.LESSON : ReviewOutcome.WRONG, state);
    }
}
