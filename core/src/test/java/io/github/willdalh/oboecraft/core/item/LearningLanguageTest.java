package io.github.willdalh.oboecraft.core.item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LearningLanguageTest {
    @Test void onlyTheSupportedExactGameLanguagesSelectTheirLists() {
        for (LearningLanguage language : LearningLanguage.values()) {
            assertEquals(language, LearningLanguage.fromGameLanguage(language.code()));
            assertEquals(language, LearningLanguage.fromCode(language.code()).orElseThrow());
        }
        for (String code : List.of("en_us", "de_ch", "de_li", "de_lu", "DE_AT", "de-AT", "nn_no", "nb_no", "nl_be", "DE_DE", "../de_de", "")) {
            assertEquals(LearningLanguage.JAPANESE, LearningLanguage.fromGameLanguage(code), code);
            assertTrue(LearningLanguage.fromCode(code).isEmpty(), code);
        }
    }

    @Test void austrianGermanHasItsOwnListWithoutReadings() {
        assertEquals(LearningLanguage.AUSTRIAN_GERMAN, LearningLanguage.fromGameLanguage("de_at"));
        assertNotEquals(LearningLanguage.GERMAN, LearningLanguage.fromGameLanguage("de_at"));
        assertFalse(LearningLanguage.AUSTRIAN_GERMAN.hasReadings());
    }

    @Test void namesUseTheLanguagesAlphabeticalOrder() {
        assertEquals(List.of("Apfel", "Äste", "Bett"),
                List.of("Bett", "Äste", "Apfel").stream().sorted(LearningLanguage.GERMAN.nameOrder()).toList());
        assertEquals(List.of("Apfel", "Äste", "Bett"),
                List.of("Bett", "Äste", "Apfel").stream().sorted(LearningLanguage.AUSTRIAN_GERMAN.nameOrder()).toList());
        assertEquals(List.of("z", "æ", "ø", "å"),
                List.of("å", "ø", "z", "æ").stream().sorted(LearningLanguage.NORWEGIAN.nameOrder()).toList());
    }
}
