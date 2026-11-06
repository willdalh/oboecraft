package io.github.willdalh.oboecraft.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NamePartTest {
    @Test
    void kanaIsReadAsItIsWrittenInHiragana() {
        assertEquals(new NamePart("オーク", "おーく"), NamePart.kana("オーク"));
        assertEquals(new NamePart("の", "の"), NamePart.kana("の"));
    }

    @Test
    void punctuationInKanaIsLeftOutOfTheReading() {
        assertEquals("じゃっくおらんたん", NamePart.kana("ジャック・オ・ランタン").reading());
    }

    @Test
    void aPartWithKanjiNeedsItsReading() {
        assertThrows(IllegalArgumentException.class, () -> NamePart.kana("原"));
        assertThrows(IllegalArgumentException.class, () -> NamePart.kana("TNT"));
    }

    @Test
    void theReadingIsHiragana() {
        assertThrows(IllegalArgumentException.class, () -> new NamePart("原", "ゲン"));
        assertThrows(IllegalArgumentException.class, () -> new NamePart("原", "gen"));
        assertThrows(IllegalArgumentException.class, () -> new NamePart("原", ""));
    }

    @Test
    void theReadingDoesntStartWithASmallKana() {
        assertThrows(IllegalArgumentException.class, () -> new NamePart("客", "ゃく"));
    }

    @Test
    void aSmallTsuCanStartAPart() {
        assertEquals("っ", NamePart.kana("っ").reading());
    }

    @Test
    void kanaCantHaveAnotherReading() {
        assertThrows(IllegalArgumentException.class, () -> new NamePart("オーク", "おおく"));
    }

    @Test
    void aPartNeedsLettersOrDigits() {
        assertThrows(IllegalArgumentException.class, () -> new NamePart("・", "なかぐろ"));
        assertThrows(IllegalArgumentException.class, () -> new NamePart(" ", "あ"));
    }
}
