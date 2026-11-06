package io.github.willdalh.oboecraft.core.review;

import io.github.willdalh.oboecraft.core.item.NameListEntry;

/** Whether a typed answer is correct for an item. */
public final class AnswerChecker {
    private AnswerChecker() {
    }

    /**
     * Whether the typed answer is the item's Japanese name or its reading. Spaces at the start and end are ignored,
     * and everything else must match exactly. Full answer checking comes in a later milestone.
     */
    public static boolean isCorrect(NameListEntry entry, String typed) {
        String answer = typed.strip();
        return answer.equals(entry.japaneseName()) || answer.equals(entry.reading());
    }
}
