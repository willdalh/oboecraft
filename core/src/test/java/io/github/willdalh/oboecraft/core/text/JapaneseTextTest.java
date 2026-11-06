package io.github.willdalh.oboecraft.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JapaneseTextTest {
    @Test
    void katakanaBecomesHiragana() {
        assertEquals("おーくのげんぼく", JapaneseText.normalize("オークノゲンボク"));
        assertEquals("ゔぁ", JapaneseText.normalize("ヴァ"));
    }

    @Test
    void aSmallKeOrKaInKatakanaStaysAsItIs() {
        assertEquals("ヶヵ", JapaneseText.normalize("ヶヵ"));
    }

    @Test
    void halfWidthKatakanaAndFullWidthLettersBecomeNormal() {
        assertEquals("おーく", JapaneseText.normalize("ｵｰｸ"));
        assertEquals("tnt", JapaneseText.normalize("ＴＮＴ"));
    }

    @Test
    void lettersBecomeLowerCase() {
        assertEquals("ookunogenboku", JapaneseText.normalize("Ooku No GENBOKU"));
    }

    @Test
    void spacesAndPunctuationAreLeftOut() {
        assertEquals("じゃっくおらんたん", JapaneseText.normalize(" ジャック・オ・ランタン！"));
        assertEquals("原木", JapaneseText.normalize("原　木。"));
    }

    @Test
    void aMacronOrCircumflexIsALongVowel() {
        assertEquals("oーku", JapaneseText.normalize("ōku"));
        assertEquals("oーku", JapaneseText.normalize("Ôku"));
    }

    @Test
    void aDashIsTheLongVowelMark() {
        assertEquals("oーku", JapaneseText.normalize("o-ku"));
        assertEquals("oーku", JapaneseText.normalize("o－ku"));
    }

    @Test
    void anApostropheIsKeptOnlyAfterAnN() {
        assertEquals("gen'boku", JapaneseText.normalize("gen’boku"));
        assertEquals("ooku", JapaneseText.normalize("o'oku"));
    }

    @Test
    void kanaIsHiraganaAndTheLongVowelMark() {
        assertTrue(JapaneseText.isKana("おーく"));
        assertFalse(JapaneseText.isKana("オーク"));
        assertFalse(JapaneseText.isKana("原"));
        assertFalse(JapaneseText.isKana(""));
    }

    @Test
    void smallKanaAreSaidWithTheKanaBefore() {
        assertTrue(JapaneseText.isSmallKana('ゃ'));
        assertTrue(JapaneseText.isSmallKana('ぃ'));
        assertFalse(JapaneseText.isSmallKana('っ'));
        assertFalse(JapaneseText.isSmallKana('や'));
    }

    @Test
    void aPartCantStartWithASmallKanaTheLongVowelMarkOrN() {
        assertFalse(JapaneseText.canStartAPart('ゃ'));
        assertFalse(JapaneseText.canStartAPart('ー'));
        assertFalse(JapaneseText.canStartAPart('ん'));
        assertTrue(JapaneseText.canStartAPart('っ'));
        assertTrue(JapaneseText.canStartAPart('か'));
    }

    @Test
    void eachKanaEndsInItsVowel() {
        assertEquals('a', JapaneseText.vowelOf('か'));
        assertEquals('a', JapaneseText.vowelOf('ゃ'));
        assertEquals('i', JapaneseText.vowelOf('ぢ'));
        assertEquals('u', JapaneseText.vowelOf('ゔ'));
        assertEquals('e', JapaneseText.vowelOf('ぇ'));
        assertEquals('o', JapaneseText.vowelOf('を'));
        assertEquals(0, JapaneseText.vowelOf('ん'));
        assertEquals(0, JapaneseText.vowelOf('っ'));
        assertEquals(0, JapaneseText.vowelOf('ー'));
    }

    @Test
    void eachVowelHasItsOwnKana() {
        assertEquals('あ', JapaneseText.kanaOfVowel('a'));
        assertEquals('お', JapaneseText.kanaOfVowel('o'));
        assertTrue(JapaneseText.isVowel('e'));
        assertFalse(JapaneseText.isVowel('n'));
    }
}
