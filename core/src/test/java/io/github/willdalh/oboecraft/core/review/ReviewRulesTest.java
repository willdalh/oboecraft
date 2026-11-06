package io.github.willdalh.oboecraft.core.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewRulesTest {
    private static final NameListEntry OAK_LOG = new NameListEntry(
            new ItemId("block.minecraft.oak_log"),
            List.of("block.minecraft.oak_log"),
            new JapaneseName(List.of(
                    NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく"))));
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final Settings SETTINGS = Settings.DEFAULTS;

    @Test
    void aReviewOfALockedItemStartsAtItsLevel() {
        SrsState locked = new SrsState.Leveled(3, Optional.of(NOW));

        assertEquals(new ReviewStart.Started(3, 2), ReviewRules.start(locked, NOW, SETTINGS));
        assertEquals(new ReviewStart.Started(0, 2), ReviewRules.start(SrsState.Leveled.NEW, NOW, SETTINGS));
    }

    @Test
    void aReviewOfAnUnlockedItemDoesntStart() {
        SrsState unlocked = new SrsState.Leveled(3, Optional.of(NOW.plusSeconds(1)));

        assertEquals(new ReviewStart.NotLocked(), ReviewRules.start(unlocked, NOW, SETTINGS));
    }

    @Test
    void aReviewOfAMasteredItemDoesntStart() {
        SrsState mastered = new SrsState.Leveled(9, Optional.empty());

        assertEquals(new ReviewStart.NotLocked(), ReviewRules.start(mastered, NOW, SETTINGS));
    }

    @Test
    void theRightAnswerIsAPass() {
        ReviewResult result = ReviewRules.answer(OAK_LOG, SrsState.Leveled.NEW, "オークの原木", NOW, SETTINGS);

        assertEquals(ReviewOutcome.PASS, result.outcome());
        assertEquals(SrsRules.pass(SrsState.Leveled.NEW, NOW, SETTINGS), result.newState());
        assertEquals(new SrsState.Leveled(1, Optional.of(NOW.plus(Duration.ofMinutes(10)))), result.newState());
    }

    @Test
    void aWrongFirstAnswerIsSavedAsAWrongAttempt() {
        SrsState locked = new SrsState.Leveled(2, Optional.of(NOW));

        ReviewResult result = ReviewRules.answer(OAK_LOG, locked, "オークの減木", NOW, SETTINGS);

        assertEquals(new ReviewResult(ReviewOutcome.WRONG,
                new SrsState.Leveled(2, Optional.of(NOW), false, 1, Optional.empty()), 2), result);
        assertEquals(new ReviewStart.Started(2, 1), ReviewRules.start(result.newState(), NOW, SETTINGS));
    }

    @Test
    void aWrongSecondAnswerIsAFail() {
        SrsState oneWrong = new SrsState.Leveled(5, Optional.of(NOW), false, 1, Optional.empty());

        ReviewResult result = ReviewRules.answer(OAK_LOG, oneWrong, "wrong", NOW, SETTINGS);

        assertEquals(new ReviewResult(ReviewOutcome.FAIL, SrsRules.fail(oneWrong, NOW, SETTINGS), 5), result);
        assertEquals(3, ((SrsState.Leveled) result.newState()).level());
    }

    @Test
    void aRightSecondAnswerIsAPass() {
        SrsState oneWrong = new SrsState.Leveled(2, Optional.of(NOW), false, 1, Optional.empty());

        ReviewResult result = ReviewRules.answer(OAK_LOG, oneWrong, "ooku no genboku", NOW, SETTINGS);

        assertEquals(ReviewOutcome.PASS, result.outcome());
        assertEquals(new SrsState.Leveled(3, Optional.of(NOW.plus(Duration.ofHours(4)))), result.newState());
    }

    @Test
    void dontKnowFailsAtOnceFromLevelOne() {
        SrsState locked = new SrsState.Leveled(1, Optional.of(NOW));

        ReviewResult result = ReviewRules.dontKnow(locked, NOW, SETTINGS);

        assertEquals(new ReviewResult(ReviewOutcome.FAIL, SrsRules.fail(locked, NOW, SETTINGS), 1), result);
    }

    @Test
    void dontKnowInALessonOnlyShowsTheAnswer() {
        assertEquals(new ReviewResult(ReviewOutcome.LESSON, SrsState.Leveled.NEW, 0),
                ReviewRules.dontKnow(SrsState.Leveled.NEW, NOW, SETTINGS));
    }

    @Test
    void aBannedItemCantBeReviewedUntilItsBanEnds() {
        Instant banEnds = NOW.plus(Duration.ofMinutes(5));
        SrsState failed = SrsRules.fail(new SrsState.Leveled(2, Optional.of(NOW)), NOW, SETTINGS);

        assertEquals(new ReviewStart.Banned(banEnds), ReviewRules.start(failed, NOW, SETTINGS));
        assertEquals(new ReviewStart.Started(1, 2), ReviewRules.start(failed, banEnds, SETTINGS));
    }

    @Test
    void onlyLeveledItemsAreAnswered() {
        assertThrows(IllegalArgumentException.class,
                () -> ReviewRules.answer(OAK_LOG, new SrsState.Known(), "genboku", NOW, SETTINGS));
        assertThrows(IllegalArgumentException.class,
                () -> ReviewRules.dontKnow(new SrsState.Ignored(), NOW, SETTINGS));
    }

    @Test
    void wrongLessonAnswersRevealTheNameWithoutChangingProgress() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(new ReviewResult(ReviewOutcome.LESSON, SrsState.Leveled.NEW, 0),
                    ReviewRules.answer(OAK_LOG, SrsState.Leveled.NEW, "wrong", NOW, SETTINGS));
        }
        assertTrue(ReviewRules.isLesson(SrsState.Leveled.NEW));
        assertFalse(ReviewRules.isLesson(new SrsState.Known()));
    }

    @Test
    void anAnswerWithoutLettersOrDigitsIsntAnAttempt() {
        assertFalse(ReviewRules.isAttempt(""));
        assertFalse(ReviewRules.isAttempt("  "));
        assertFalse(ReviewRules.isAttempt("。"));
        assertFalse(ReviewRules.isAttempt("・、!"));
        assertFalse(ReviewRules.isAttempt("'"));
        assertTrue(ReviewRules.isAttempt("つち"));
        assertThrows(IllegalArgumentException.class,
                () -> ReviewRules.answer(OAK_LOG, SrsState.Leveled.NEW, " ", NOW, SETTINGS));
    }
}
