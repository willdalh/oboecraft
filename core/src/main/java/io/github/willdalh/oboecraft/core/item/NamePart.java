package io.github.willdalh.oboecraft.core.item;

import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.util.Objects;

/**
 * One part of a Japanese name with its reading: one kanji, like {@code 原} read {@code げん}, kanji that are only read
 * together, like {@code 今日} read {@code きょう}, a run of kana, or the particle {@code の}. An answer may write each
 * part as its text or as its reading.
 *
 * @param text    the part as the Japanese name writes it
 * @param reading how the part is said in this name, in hiragana and the long vowel mark
 */
public record NamePart(String text, String reading) {
    public NamePart {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(reading, "reading");
        String normalizedText = JapaneseText.normalize(text);
        if (normalizedText.isEmpty()) {
            throw new IllegalArgumentException("The part " + text + " has no letters");
        }
        if (!JapaneseText.isKana(reading)) {
            throw new IllegalArgumentException("The reading " + reading + " of " + text + " isn't hiragana");
        }
        if (!JapaneseText.canStartAPart(reading.charAt(0))) {
            throw new IllegalArgumentException("The reading " + reading + " of " + text
                    + " starts with a kana that belongs to the part before it");
        }
        if (JapaneseText.isKana(normalizedText) && !normalizedText.equals(reading)) {
            throw new IllegalArgumentException("The kana " + text + " can't be read " + reading);
        }
    }

    /**
     * A part written only in kana, which is read as it is written.
     *
     * @throws IllegalArgumentException if the text has anything other than kana and punctuation, like a kanji, which
     *     needs its reading given
     */
    public static NamePart kana(String text) {
        String reading = JapaneseText.normalize(text);
        if (!JapaneseText.isKana(reading)) {
            throw new IllegalArgumentException("The part " + text + " isn't only kana, so it needs a reading");
        }
        return new NamePart(text, reading);
    }
}
