package io.github.willdalh.oboecraft.tools.namelist;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Guesses a name's parts and readings: a text splitter splits the name into words and reads them, JMdict checks each
 * word written with kanji, and KANJIDIC2 splits its reading between its kanji. A run of kana is one part, and the
 * particle の its own. Whatever the dictionaries don't agree on is noted for review.
 */
final class DictionaryNameGuesser implements NameGuesser {
    private static final String PARTICLE_NO = "の";
    /** How each Latin letter is read when spelled out, as in TNT. */
    private static final String[] LETTER_READINGS = {
        "えー", "びー", "しー", "でぃー", "いー", "えふ", "じー", "えいち", "あい", "じぇー", "けー", "える", "えむ",
        "えぬ", "おー", "ぴー", "きゅー", "あーる", "えす", "てぃー", "ゆー", "ぶい", "だぶりゅー", "えっくす", "わい",
        "ぜっと",
    };

    private final WordSplitter words;
    private final Jmdict jmdict;
    private final KanjiSplitter kanjiSplitter;

    DictionaryNameGuesser(WordSplitter words, Jmdict jmdict, KanjiSplitter kanjiSplitter) {
        this.words = Objects.requireNonNull(words, "words");
        this.jmdict = Objects.requireNonNull(jmdict, "jmdict");
        this.kanjiSplitter = Objects.requireNonNull(kanjiSplitter, "kanjiSplitter");
    }

    /**
     * A part while the name is built, whether it is the particle の, which is never joined to other kana, and the
     * index of the word it is from.
     */
    private record Piece(NamePart part, boolean particle, int word) {
        boolean kana() {
            return particle || JapaneseText.isKana(JapaneseText.normalize(part.text()));
        }
    }

    @Override
    public GuessedName guess(String japaneseName) {
        List<Piece> pieces = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        String punctuationBefore = "";
        List<Word> nameWords = words.split(japaneseName);
        for (int wordIndex = 0; wordIndex < nameWords.size(); wordIndex++) {
            Word word = nameWords.get(wordIndex);
            if (JapaneseText.normalize(word.text()).isEmpty()) {
                // Punctuation, like the ・ in ジャック・オ・ランタン, belongs to the text of the part before it.
                if (pieces.isEmpty()) {
                    punctuationBefore += word.text();
                } else {
                    Piece before = pieces.removeLast();
                    NamePart part = new NamePart(before.part().text() + word.text(), before.part().reading());
                    pieces.add(new Piece(part, before.particle(), before.word()));
                }
                continue;
            }
            int firstOfWord = pieces.size();
            if (word.particle() && word.text().equals(PARTICLE_NO)) {
                pieces.add(new Piece(NamePart.kana(PARTICLE_NO), true, wordIndex));
            } else if (word.text().codePoints().anyMatch(DictionaryNameGuesser::isLatinLetterOrDigit)) {
                NamePart part = new NamePart(word.text(), lettersReadingOf(word));
                notes.add(word.text() + " is read " + part.reading());
                pieces.add(new Piece(part, false, wordIndex));
            } else if (word.text().codePoints().anyMatch(KanjiSplitter::needsReading)) {
                String reading = word.reading();
                if (JapaneseText.isKana(reading)) {
                    notes.addAll(checkWithJmdict(word));
                } else {
                    reading = kanjiSplitter.guessReading(word.text());
                    notes.add("Sudachi has no reading for " + word.text() + ", so each kanji is given its first "
                            + "reading in KANJIDIC2: " + reading);
                }
                KanjiSplitter.Split split = kanjiSplitter.split(word.text(), reading);
                int index = wordIndex;
                split.parts().forEach(part -> pieces.add(new Piece(part, false, index)));
                notes.addAll(split.notes());
            } else {
                pieces.add(new Piece(NamePart.kana(word.text()), false, wordIndex));
            }
            if (!punctuationBefore.isEmpty()) {
                Piece first = pieces.get(firstOfWord);
                NamePart part = new NamePart(punctuationBefore + first.part().text(), first.part().reading());
                pieces.set(firstOfWord, new Piece(part, first.particle(), first.word()));
                punctuationBefore = "";
            }
        }
        notes.addAll(longVowelsBetween(pieces));
        JapaneseName name = new JapaneseName(joinKana(pieces));
        if (!name.text().equals(japaneseName)) {
            throw new IllegalStateException("The parts of " + japaneseName + " make up " + name.text());
        }
        return new GuessedName(name, notes);
    }

    /** What JMdict says about the reading of a word written with kanji, if it doesn't simply agree. */
    private List<String> checkWithJmdict(Word word) {
        List<String> notes = new ArrayList<>();
        if (!word.known()) {
            notes.add("Sudachi doesn't know " + word.text() + " and guessed " + word.reading());
        }
        String text = word.text();
        String reading = word.reading();
        Set<String> readings = jmdict.readingsOf(text);
        if (readings.isEmpty() && !word.dictionaryForm().equals(text)) {
            text = word.dictionaryForm();
            reading = word.dictionaryFormReading();
            readings = jmdict.readingsOf(text);
        }
        if (readings.isEmpty()) {
            notes.add(word.text() + " (" + word.reading() + ") isn't in JMdict");
        } else if (!readings.contains(reading)) {
            notes.add("Sudachi reads " + text + " " + reading + ", but JMdict reads it " + String.join(", ", readings));
        }
        return notes;
    }

    /**
     * A word with Latin letters or digits: spelled out if it is only letters, like TNT, and otherwise as the text
     * splitter reads it.
     */
    private static String lettersReadingOf(Word word) {
        String text = word.text();
        if (!text.chars().allMatch(c -> c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
            return word.reading();
        }
        StringBuilder reading = new StringBuilder();
        text.toLowerCase(Locale.ROOT).chars().forEach(c -> reading.append(LETTER_READINGS[c - 'a']));
        return reading.toString();
    }

    private static boolean isLatinLetterOrDigit(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN || Character.isDigit(codePoint);
    }

    /**
     * Notes where two parts of one word meet in what could be a long vowel, like こ and うり in 小売. A long vowel is
     * only matched inside one part, so it is worth checking whether the parts are said separately. Separate words are
     * said separately anyway.
     */
    private static List<String> longVowelsBetween(List<Piece> pieces) {
        List<String> notes = new ArrayList<>();
        for (int i = 1; i < pieces.size(); i++) {
            if (pieces.get(i - 1).word() != pieces.get(i).word()) {
                continue;
            }
            NamePart before = pieces.get(i - 1).part();
            NamePart after = pieces.get(i).part();
            char vowel = JapaneseText.vowelOf(before.reading().charAt(before.reading().length() - 1));
            char next = after.reading().charAt(0);
            if (vowel != 0 && lengthens(vowel, next)) {
                notes.add(before.text() + " (" + before.reading() + ") and " + after.text() + " (" + after.reading()
                        + ") meet in what could be a long vowel");
            }
        }
        return notes;
    }

    /** Whether a kana after a vowel makes it long: the same vowel, い after e, or う after o. */
    private static boolean lengthens(char vowel, char next) {
        return switch (next) {
            case 'あ' -> vowel == 'a';
            case 'い' -> vowel == 'i' || vowel == 'e';
            case 'う' -> vowel == 'u' || vowel == 'o';
            case 'え' -> vowel == 'e';
            case 'お' -> vowel == 'o';
            default -> false;
        };
    }

    /**
     * The parts, with kana next to each other joined into one part, except the particle の. Kana from different words
     * is only joined if both are in the same script, like the hiragana された or the katakana ハーフブロック, so the
     * okurigana of one word stays apart from a katakana word after it, like the だ and ナイリウム of 歪んだナイリウム.
     */
    private static List<NamePart> joinKana(List<Piece> pieces) {
        List<NamePart> parts = new ArrayList<>();
        Piece previous = null;
        for (Piece piece : pieces) {
            boolean sameWordOrScript = previous != null
                    && (previous.word() == piece.word() || scriptOf(previous) == scriptOf(piece));
            if (sameWordOrScript && joinable(previous) && joinable(piece)) {
                NamePart joined = NamePart.kana(parts.removeLast().text() + piece.part().text());
                parts.add(joined);
                previous = new Piece(joined, false, piece.word());
            } else {
                parts.add(piece.part());
                previous = piece;
            }
        }
        return parts;
    }

    /**
     * The script of a part in kana: hiragana, katakana, or null for a mix. The long vowel mark and punctuation like ・
     * are in both scripts, so they don't count.
     */
    private static Character.UnicodeScript scriptOf(Piece piece) {
        Character.UnicodeScript script = null;
        for (int c : piece.part().text().codePoints().toArray()) {
            Character.UnicodeScript of = Character.UnicodeScript.of(c);
            if (of != Character.UnicodeScript.HIRAGANA && of != Character.UnicodeScript.KATAKANA) {
                continue;
            }
            if (c == JapaneseText.LONG_VOWEL_MARK || c == '・') {
                continue;
            }
            if (script != null && script != of) {
                return null;
            }
            script = of;
        }
        return script;
    }

    private static boolean joinable(Piece piece) {
        return !piece.particle() && piece.kana();
    }
}
