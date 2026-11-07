package io.github.willdalh.oboecraft.tools.readingclips;

import java.util.Objects;
import java.util.Optional;

/**
 * One mora as VOICEVOX says it: its kana, its consonant, if any, and its vowel. The vowel is one of {@code a}, {@code i},
 * {@code u}, {@code e} and {@code o}, in capitals when it is devoiced, or {@code N} for ん and {@code cl} for っ.
 */
record Mora(String text, Optional<String> consonant, String vowel) {
    Mora {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(consonant, "consonant");
        Objects.requireNonNull(vowel, "vowel");
    }
}
