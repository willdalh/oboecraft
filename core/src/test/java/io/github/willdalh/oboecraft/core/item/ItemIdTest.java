package io.github.willdalh.oboecraft.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ItemIdTest {
    @Test
    void anIdIsATranslationKeyOfAtMostTheLongestLength() {
        String longest = "a".repeat(ItemId.MAX_LENGTH);

        assertEquals(longest, new ItemId(longest).value());
        assertThrows(IllegalArgumentException.class, () -> new ItemId(longest + "a"));
        assertThrows(IllegalArgumentException.class, () -> new ItemId(" "));
    }
}
