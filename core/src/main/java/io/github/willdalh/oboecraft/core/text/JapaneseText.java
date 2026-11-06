package io.github.willdalh.oboecraft.core.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Rules for Japanese text that the name list and answer checking share: how text is normalized before it is compared,
 * and what kind of kana a character is.
 */
public final class JapaneseText {
    /** The long vowel mark, which is the same in hiragana and katakana text. */
    public static final char LONG_VOWEL_MARK = 'ー';

    private static final char FIRST_HIRAGANA = 'ぁ';
    private static final char LAST_HIRAGANA = 'ゖ';
    private static final char FIRST_KATAKANA = 'ァ';
    private static final char LAST_KATAKANA = 'ヶ';
    private static final int KATAKANA_TO_HIRAGANA = FIRST_KATAKANA - FIRST_HIRAGANA;
    private static final String SMALL_KANA = "ぁぃぅぇぉゃゅょゎゕゖ";
    private static final String DASHES = "-‐‑‒–—―−";
    private static final String APOSTROPHES = "'‘’ʼ";

    private JapaneseText() {
    }

    /**
     * Normalizes text so that writing the same thing in another way gives the same result:
     *
     * <ul>
     *   <li>full-width letters and digits become plain ones, and half-width katakana full-width ones (Unicode NFKC),
     *   <li>letters become lower case,
     *   <li>katakana become hiragana,
     *   <li>a vowel with a macron or circumflex, like {@code ō}, becomes the vowel and the long vowel mark,
     *   <li>a dash becomes the long vowel mark, as when typing on a Japanese keyboard,
     *   <li>an apostrophe is kept only after an {@code n}, where it ends {@code ん} in romaji, and
     *   <li>everything else that isn't a letter or digit, like spaces and {@code ・}, is left out.
     * </ul>
     */
    public static String normalize(String text) {
        String composed = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(composed.length());
        composed.codePoints().forEach(codePoint -> {
            char longVowel = longVowelOf(codePoint);
            if (longVowel != 0) {
                normalized.append(longVowel).append(LONG_VOWEL_MARK);
            } else if (DASHES.indexOf(codePoint) >= 0) {
                normalized.append(LONG_VOWEL_MARK);
            } else if (APOSTROPHES.indexOf(codePoint) >= 0) {
                if (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) == 'n') {
                    normalized.append('\'');
                }
            } else if (Character.isLetterOrDigit(codePoint)) {
                normalized.appendCodePoint(toHiragana(codePoint));
            }
        });
        return normalized.toString();
    }

    /** Whether text is made only of hiragana and the long vowel mark. */
    public static boolean isKana(String text) {
        return !text.isEmpty() && text.chars().allMatch(c -> isKana((char) c));
    }

    /** Whether a character is a hiragana or the long vowel mark. */
    public static boolean isKana(char c) {
        return (c >= FIRST_HIRAGANA && c <= LAST_HIRAGANA) || c == LONG_VOWEL_MARK;
    }

    /**
     * Whether a character is a small hiragana that is said together with the kana before it, like {@code ゃ} in
     * {@code きゃ}. The small {@code っ} isn't one, since it is a sound of its own.
     */
    public static boolean isSmallKana(char c) {
        return SMALL_KANA.indexOf(c) >= 0;
    }

    /**
     * The vowel a hiragana ends in, as one of {@code a}, {@code i}, {@code u}, {@code e} and {@code o}, or 0 for
     * {@code ん}, {@code っ} and characters that aren't hiragana. The long vowel mark has no vowel of its own.
     */
    public static char vowelOf(char c) {
        if ("あぁかがさざただなはばぱまやゃらわゎゕ".indexOf(c) >= 0) {
            return 'a';
        }
        if ("いぃきぎしじちぢにひびぴみりゐ".indexOf(c) >= 0) {
            return 'i';
        }
        if ("うぅくぐすずつづぬふぶぷむゆゅるゔ".indexOf(c) >= 0) {
            return 'u';
        }
        if ("えぇけげせぜてでねへべぺめれゑゖ".indexOf(c) >= 0) {
            return 'e';
        }
        if ("おぉこごそぞとどのほぼぽもよょろを".indexOf(c) >= 0) {
            return 'o';
        }
        return 0;
    }

    private static int toHiragana(int codePoint) {
        if (codePoint >= FIRST_KATAKANA && codePoint <= LAST_KATAKANA) {
            return codePoint - KATAKANA_TO_HIRAGANA;
        }
        return codePoint;
    }

    private static char longVowelOf(int codePoint) {
        return switch (codePoint) {
            case 'ā', 'â' -> 'a';
            case 'ī', 'î' -> 'i';
            case 'ū', 'û' -> 'u';
            case 'ē', 'ê' -> 'e';
            case 'ō', 'ô' -> 'o';
            default -> 0;
        };
    }
}
