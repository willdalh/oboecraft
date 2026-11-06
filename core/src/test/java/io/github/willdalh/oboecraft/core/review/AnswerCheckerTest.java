package io.github.willdalh.oboecraft.core.review;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerCheckerTest {
    private static final NameListEntry OAK_LOG = new NameListEntry(
            new ItemId("block.minecraft.oak_log"), List.of("block.minecraft.oak_log"), "オークの原木", "おーくのげんぼく");

    @Test
    void theJapaneseNamePasses() {
        assertTrue(AnswerChecker.isCorrect(OAK_LOG, "オークの原木"));
    }

    @Test
    void theReadingPasses() {
        assertTrue(AnswerChecker.isCorrect(OAK_LOG, "おーくのげんぼく"));
    }

    @Test
    void spacesAtTheStartAndEndAreIgnored() {
        assertTrue(AnswerChecker.isCorrect(OAK_LOG, " オークの原木　"));
    }

    @Test
    void wrongKanjiWithTheSameReadingFails() {
        assertFalse(AnswerChecker.isCorrect(OAK_LOG, "オークの減木"));
    }

    @Test
    void anythingElseFails() {
        assertFalse(AnswerChecker.isCorrect(OAK_LOG, "オーク"));
        assertFalse(AnswerChecker.isCorrect(OAK_LOG, "ooku no genboku"));
        assertFalse(AnswerChecker.isCorrect(OAK_LOG, "オークの原木の"));
    }
}
