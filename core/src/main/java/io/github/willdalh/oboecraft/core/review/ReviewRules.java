package io.github.willdalh.oboecraft.core.review;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.time.Instant;

/** The rules of a review: when one can start, and what an answer or Don't know does. */
public final class ReviewRules {
    /** A review has two attempts: a wrong first answer leaves one, and a wrong second answer is a fail. */
    public static final int ATTEMPTS = SrsState.Leveled.MAX_WRONG_ATTEMPTS + 1;

    private ReviewRules() {
    }

    /**
     * Whether a review of the item can start. It can only start for a locked item, so reviewing early isn't possible,
     * and not while the item is banned. Uses {@link SrsRules#isLocked} and {@link SrsRules#isBanned}, so it agrees
     * with the lock check. The attempts left count the wrong attempt saved from an earlier review of the item.
     */
    public static ReviewStart start(SrsState state, Instant now, Settings settings) {
        if (!SrsRules.isLocked(state, now, settings)) {
            return new ReviewStart.NotLocked();
        }
        return switch (state) {
            case SrsState.Leveled leveled when SrsRules.isBanned(leveled, now, settings) ->
                    new ReviewStart.Banned(leveled.bannedUntil().orElseThrow());
            case SrsState.Leveled leveled -> new ReviewStart.Started(
                    leveled.level(), isLesson(leveled) ? ATTEMPTS : ATTEMPTS - leveled.wrongAttempts());
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
     * The result of an answer in a review of a locked, leveled item. A correct answer is a pass. In a lesson, a wrong
     * answer shows the answer and changes nothing. Otherwise a wrong first answer is saved as a wrong attempt, and a
     * wrong second answer is a fail.
     *
     * @throws IllegalArgumentException if the typed text isn't an attempt, see {@link #isAttempt}, or the state isn't
     *                                  leveled
     */
    public static ReviewResult answer(
            NameListEntry entry, SrsState state, String typed, Instant now, Settings settings) {
        if (!isAttempt(typed)) {
            throw new IllegalArgumentException("An answer with no letter or digit isn't an attempt");
        }
        SrsState.Leveled leveled = leveled(state);
        if (AnswerChecker.isCorrect(entry, typed)) {
            return new ReviewResult(ReviewOutcome.PASS, SrsRules.pass(leveled, now, settings), leveled.level());
        }
        if (isLesson(leveled)) {
            return new ReviewResult(ReviewOutcome.LESSON, leveled, leveled.level());
        }
        if (leveled.wrongAttempts() < SrsState.Leveled.MAX_WRONG_ATTEMPTS) {
            return new ReviewResult(
                    ReviewOutcome.WRONG, leveled.withWrongAttempts(leveled.wrongAttempts() + 1), leveled.level());
        }
        return fail(leveled, now, settings);
    }

    /**
     * The result of Don't know in a review of a locked, leveled item. In a lesson, it shows the answer and changes
     * nothing. Otherwise it is a fail at once.
     *
     * @throws IllegalArgumentException if the state isn't leveled
     */
    public static ReviewResult dontKnow(SrsState state, Instant now, Settings settings) {
        SrsState.Leveled leveled = leveled(state);
        return isLesson(leveled)
                ? new ReviewResult(ReviewOutcome.LESSON, leveled, leveled.level())
                : fail(leveled, now, settings);
    }

    private static ReviewResult fail(SrsState.Leveled leveled, Instant now, Settings settings) {
        return new ReviewResult(ReviewOutcome.FAIL, SrsRules.fail(leveled, now, settings), leveled.level());
    }

    private static SrsState.Leveled leveled(SrsState state) {
        if (!(state instanceof SrsState.Leveled leveled)) {
            throw new IllegalArgumentException("Only leveled items are reviewed, not " + state);
        }
        return leveled;
    }
}
