package io.github.willdalh.oboecraft.tools.namelist;

import java.util.Objects;

/**
 * A word in a Japanese name, as the text splitter finds it.
 *
 * @param text                  the word as it is written in the name
 * @param reading               how the word is read here, in hiragana
 * @param dictionaryForm        the word's dictionary form, like 光る for 光り, which JMdict lists
 * @param dictionaryFormReading the reading of the dictionary form, in hiragana
 * @param particle              whether the word is a particle, like の
 * @param known                 whether the text splitter's dictionary has the word, instead of guessing it
 */
record Word(String text, String reading, String dictionaryForm, String dictionaryFormReading, boolean particle,
        boolean known) {
    Word {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(dictionaryForm, "dictionaryForm");
        Objects.requireNonNull(dictionaryFormReading, "dictionaryFormReading");
    }

    /** A word in its dictionary form that the dictionary knows, for tests. */
    static Word of(String text, String reading) {
        return new Word(text, reading, text, reading, false, true);
    }

    /** The particle の. */
    static Word particleNo() {
        return new Word("の", "の", "の", "の", true, true);
    }
}
