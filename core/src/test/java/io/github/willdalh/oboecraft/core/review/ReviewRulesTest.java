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

        assertEquals(new ReviewStart.Started(3), ReviewRules.start(locked, NOW, SETTINGS));
        assertEquals(new ReviewStart.Started(0), ReviewRules.start(SrsState.Leveled.NEW, NOW, SETTINGS));
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
    void aWrongAnswerLeavesTheStateAlone() {
        SrsState locked = new SrsState.Leveled(2, Optional.of(NOW));

        ReviewResult result = ReviewRules.answer(OAK_LOG, locked, "オークの減木", NOW, SETTINGS);

        assertEquals(new ReviewResult(ReviewOutcome.WRONG, locked), result);
    }

    @Test
    void wrongLessonAnswersRevealTheNameWithoutChangingProgress() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(new ReviewResult(ReviewOutcome.LESSON, SrsState.Leveled.NEW),
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
