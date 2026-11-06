package io.github.willdalh.oboecraft.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class JapaneseNameTest {
    private static final List<NamePart> OAK_LOG = List.of(
            NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく"));

    @Test
    void theTextAndReadingAreThePartsJoined() {
        JapaneseName name = new JapaneseName(OAK_LOG);

        assertEquals("オークの原木", name.text());
        assertEquals("おーくのげんぼく", name.reading());
    }

    @Test
    void eachPositionInTheReadingKnowsItsPart() {
        JapaneseName name = new JapaneseName(OAK_LOG);

        assertEquals(new JapaneseName.PlacedPart(NamePart.kana("オーク"), "おーく", 0, 3), name.partAt(2));
        assertEquals(new JapaneseName.PlacedPart(new NamePart("原", "げん"), "原", 4, 6), name.partAt(5));
    }

    @Test
    void theVowelBeforeAPositionIsOnlyInTheSamePart() {
        JapaneseName name = new JapaneseName(OAK_LOG);

        assertEquals('o', name.vowelBefore(1), "after お");
        assertEquals('o', name.vowelBefore(2), "after ー, which carries the お");
        assertEquals(0, name.vowelBefore(3), "the start of の");
        assertEquals(0, name.vowelBefore(4), "the start of 原");
        assertEquals('e', name.vowelBefore(5), "after げ");
    }

    @Test
    void namesWithTheSamePartsAreEqual() {
        assertEquals(new JapaneseName(OAK_LOG), new JapaneseName(OAK_LOG));
        assertEquals(new JapaneseName(OAK_LOG).hashCode(), new JapaneseName(OAK_LOG).hashCode());
    }

    @Test
    void namesThatSplitTheSameTextDifferentlyArentEqual() {
        JapaneseName perKanji = new JapaneseName(List.of(new NamePart("砂", "じゃ"), new NamePart("利", "り")));
        JapaneseName oneUnit = new JapaneseName(List.of(new NamePart("砂利", "じゃり")));

        assertEquals(perKanji.text(), oneUnit.text());
        assertNotEquals(perKanji, oneUnit);
    }

    @Test
    void aNameNeedsAPart() {
        assertThrows(IllegalArgumentException.class, () -> new JapaneseName(List.of()));
    }
}
