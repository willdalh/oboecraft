package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NamePart;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DictionaryNameGuesserTest {
    private static final Jmdict JMDICT = jmdict(Map.of(
            "原木", "げんぼく",
            "付き", "つき",
            "小売", "こうり",
            "王", "おう"));
    private static final Kanjidic KANJIDIC = Kanjidic.of(Map.of(
            "原", Set.of("げん"),
            "木", Set.of("ぼく", "き"),
            "付", Set.of("ふ", "つ"),
            "金", Set.of("きん", "かね"),
            "小", Set.of("しょう", "こ"),
            "売", Set.of("ばい", "う", "うり"),
            "王", Set.of("おう")));

    /** A JMdict with one entry per word, whose readings are separated by commas. */
    private static Jmdict jmdict(Map<String, String> readings) {
        StringBuilder xml = new StringBuilder("<JMdict>");
        readings.forEach((word, wordReadings) -> {
            xml.append("<entry><k_ele><keb>").append(word).append("</keb></k_ele>");
            for (String reading : wordReadings.split(",")) {
                xml.append("<r_ele><reb>").append(reading).append("</reb></r_ele>");
            }
            xml.append("</entry>");
        });
        xml.append("</JMdict>");
        try {
            return Jmdict.read(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Guesses with a text splitter that always splits the name into these words. */
    private static GuessedName guess(String name, Word... words) {
        WordSplitter splitter = text -> {
            assertEquals(name, text);
            return List.of(words);
        };
        return new DictionaryNameGuesser(splitter, JMDICT, new KanjiSplitter(KANJIDIC)).guess(name);
    }

    @Test
    void kanaIsReadAsWrittenTheParticleNoIsItsOwnPartAndKanjiAreSplit() {
        GuessedName guess = guess("オークの原木", Word.of("オーク", "おーく"), Word.particleNo(), Word.of("原木", "げんぼく"));

        JapaneseName expected = new JapaneseName(List.of(
                NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく")));
        assertEquals(expected, guess.name());
        assertTrue(guess.notes().isEmpty(), guess.notes().toString());
    }

    @Test
    void lettersAreSpelledOutAndNoted() {
        GuessedName guess = guess("TNT付きのトロッコ",
                Word.of("TNT", "TNT"), Word.of("付き", "つき"), Word.particleNo(), Word.of("トロッコ", "とろっこ"));

        assertEquals(List.of(new NamePart("TNT", "てぃーえぬてぃー"), new NamePart("付", "つ"), NamePart.kana("き"),
                NamePart.kana("の"), NamePart.kana("トロッコ")), guess.name().parts());
        assertEquals(List.of("TNT is read てぃーえぬてぃー"), guess.notes());
    }

    @Test
    void hiraganaWordsNextToEachOtherAreOnePart() {
        GuessedName guess = guess("付きされた",
                Word.of("付き", "つき"), Word.of("さ", "さ"), Word.of("れ", "れ"), Word.of("た", "た"));

        assertEquals(List.of(new NamePart("付", "つ"), NamePart.kana("きされた")), guess.name().parts());
    }

    @Test
    void katakanaWordsNextToEachOtherAreOnePart() {
        GuessedName guess = guess("ハーフブロック", Word.of("ハーフ", "はーふ"), Word.of("ブロック", "ぶろっく"));

        assertEquals(List.of(NamePart.kana("ハーフブロック")), guess.name().parts());
    }

    @Test
    void aKatakanaWordIsApartFromTheKanaBeforeIt() {
        GuessedName guess = guess("付きトロッコ", Word.of("付き", "つき"), Word.of("トロッコ", "とろっこ"));

        assertEquals(List.of(new NamePart("付", "つ"), NamePart.kana("き"), NamePart.kana("トロッコ")),
                guess.name().parts());
    }

    @Test
    void punctuationIsPartOfTheTextOfThePartBeforeIt() {
        GuessedName guess = guess("ジャック・オ・ランタン", Word.of("ジャック", "じゃっく"), Word.of("・", ""),
                Word.of("オ", "お"), Word.of("・", ""), Word.of("ランタン", "らんたん"));

        assertEquals(List.of(NamePart.kana("ジャック・オ・ランタン")), guess.name().parts());
    }

    @Test
    void aWordJmdictDoesntHaveIsNoted() {
        GuessedName guess = guess("木台", Word.of("木台", "きだい"));

        assertTrue(guess.notes().contains("木台 (きだい) isn't in JMdict"), guess.notes().toString());
    }

    @Test
    void aReadingJmdictDoesntHaveIsNoted() {
        GuessedName guess = guess("原木", Word.of("原木", "はらき"));

        assertTrue(guess.notes().contains("Sudachi reads 原木 はらき, but JMdict reads it げんぼく"),
                guess.notes().toString());
    }

    @Test
    void wordsThatMeetInWhatCouldBeALongVowelArentNoted() {
        GuessedName guess = guess("キノコ王", Word.of("キノコ", "きのこ"), Word.of("王", "おう"));

        assertEquals(List.of(), guess.notes());
    }

    @Test
    void aWordSudachiDoesntKnowIsNoted() {
        GuessedName guess = guess("原木", new Word("原木", "げんぼく", "原木", "げんぼく", false, false));

        assertEquals(List.of("Sudachi doesn't know 原木 and guessed げんぼく"), guess.notes());
    }

    @Test
    void partsThatMeetInWhatCouldBeALongVowelAreNoted() {
        GuessedName guess = guess("小売", Word.of("小売", "こうり"));

        assertTrue(guess.notes().contains("小 (こ) and 売 (うり) meet in what could be a long vowel"),
                guess.notes().toString());
    }
}
