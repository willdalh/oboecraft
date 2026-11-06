package io.github.willdalh.oboecraft.core.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The romaji that Japanese keyboards turn into hiragana, like {@code shi} and {@code si} for {@code し}. It follows the
 * usual typing rules rather than Hepburn, so every kana has its own spelling: {@code づ} is {@code du}, {@code ぢ} is
 * {@code di} and {@code を} is {@code wo}.
 *
 * <p>Two sounds depend on the letters around them, so they aren't in the table: a single {@code n} for {@code ん}, and
 * a doubled consonant for {@code っ}. Answer checking handles those.
 */
public final class Romaji {
    /** One spelling: the romaji letters and the hiragana they are typed as. */
    public record Spelling(String romaji, String kana) {
    }

    /** The five vowels in the order of the kana in each row of {@link #ROWS}. */
    private static final String VOWELS = "aiueo";
    private static final String SMALL_Y_KANA = "ゃぃゅぇょ";

    /**
     * Each consonant and its kana for the vowels a, i, u, e and o. A row with more than one kana for a vowel separates
     * them with {@code ,}.
     */
    private static final String[][] ROWS = {
        {"", "あ,い,う,え,お"},
        {"k", "か,き,く,け,こ"},
        {"g", "が,ぎ,ぐ,げ,ご"},
        {"s", "さ,し,す,せ,そ"},
        {"z", "ざ,じ,ず,ぜ,ぞ"},
        {"t", "た,ち,つ,て,と"},
        {"d", "だ,ぢ,づ,で,ど"},
        {"n", "な,に,ぬ,ね,の"},
        {"h", "は,ひ,ふ,へ,ほ"},
        {"b", "ば,び,ぶ,べ,ぼ"},
        {"p", "ぱ,ぴ,ぷ,ぺ,ぽ"},
        {"m", "ま,み,む,め,も"},
        {"y", "や,い,ゆ,いぇ,よ"},
        {"r", "ら,り,る,れ,ろ"},
        {"w", "わ,うぃ,う,うぇ,を"},
        {"c", "か,し,く,せ,こ"},
        {"q", "くぁ,くぃ,く,くぇ,くぉ"},
        {"f", "ふぁ,ふぃ,ふ,ふぇ,ふぉ"},
        {"v", "ゔぁ,ゔぃ,ゔ,ゔぇ,ゔぉ"},
        {"j", "じゃ,じ,じゅ,じぇ,じょ"},
        {"sh", "しゃ,し,しゅ,しぇ,しょ"},
        {"ch", "ちゃ,ち,ちゅ,ちぇ,ちょ"},
        {"ts", "つぁ,つぃ,つ,つぇ,つぉ"},
        {"th", "てゃ,てぃ,てゅ,てぇ,てょ"},
        {"dh", "でゃ,でぃ,でゅ,でぇ,でょ"},
        {"tw", "とぁ,とぃ,とぅ,とぇ,とぉ"},
        {"dw", "どぁ,どぃ,どぅ,どぇ,どぉ"},
        {"wh", "うぁ,うぃ,う,うぇ,うぉ"},
        {"kw", "くぁ,くぃ,く,くぇ,くぉ"},
        {"gw", "ぐぁ,ぐぃ,ぐ,ぐぇ,ぐぉ"},
        {"x", "ぁ,ぃ,ぅ,ぇ,ぉ"},
        {"l", "ぁ,ぃ,ぅ,ぇ,ぉ"},
    };

    /**
     * Each consonant that is typed with a {@code y} for the small {@code ゃ}, {@code ぃ}, {@code ゅ}, {@code ぇ} and
     * {@code ょ}, like {@code kya} for {@code きゃ}, and the kana before the small one. {@code xy} and {@code ly} type
     * the small kana on their own.
     */
    private static final String[][] Y_ROWS = {
        {"k", "き"}, {"g", "ぎ"}, {"s", "し"}, {"z", "じ"}, {"j", "じ"}, {"t", "ち"}, {"c", "ち"}, {"d", "ぢ"},
        {"n", "に"}, {"h", "ひ"}, {"b", "び"}, {"p", "ぴ"}, {"m", "み"}, {"r", "り"}, {"f", "ふ"}, {"v", "ゔ"},
        {"x", ""}, {"l", ""},
    };

    /** Spellings that don't follow a row. */
    private static final String[][] OTHERS = {
        {"nn", "ん"}, {"n'", "ん"}, {"xn", "ん"},
        {"xtu", "っ"}, {"ltu", "っ"}, {"xtsu", "っ"}, {"ltsu", "っ"},
        {"xwa", "ゎ"}, {"lwa", "ゎ"}, {"xka", "ゕ"}, {"lka", "ゕ"}, {"xke", "ゖ"}, {"lke", "ゖ"},
    };

    private static final Map<Character, List<Spelling>> SPELLINGS_BY_FIRST_LETTER = buildSpellings();

    private Romaji() {
    }

    /** Every spelling that starts with this letter, or none if no spelling does. */
    public static List<Spelling> spellingsStartingWith(char letter) {
        return SPELLINGS_BY_FIRST_LETTER.getOrDefault(letter, List.of());
    }

    private static Map<Character, List<Spelling>> buildSpellings() {
        Map<String, String> kanaByRomaji = new HashMap<>();
        for (String[] row : ROWS) {
            String[] kana = row[1].split(",");
            for (int vowel = 0; vowel < VOWELS.length(); vowel++) {
                add(kanaByRomaji, row[0] + VOWELS.charAt(vowel), kana[vowel]);
            }
        }
        for (String[] row : Y_ROWS) {
            for (int vowel = 0; vowel < VOWELS.length(); vowel++) {
                add(kanaByRomaji, row[0] + "y" + VOWELS.charAt(vowel), row[1] + SMALL_Y_KANA.charAt(vowel));
            }
        }
        for (String[] other : OTHERS) {
            add(kanaByRomaji, other[0], other[1]);
        }
        Map<Character, List<Spelling>> byFirstLetter = new HashMap<>();
        kanaByRomaji.forEach((romaji, kana) -> byFirstLetter
                .computeIfAbsent(romaji.charAt(0), letter -> new ArrayList<>())
                .add(new Spelling(romaji, kana)));
        byFirstLetter.replaceAll((letter, spellings) -> List.copyOf(spellings));
        return Map.copyOf(byFirstLetter);
    }

    private static void add(Map<String, String> kanaByRomaji, String romaji, String kana) {
        String other = kanaByRomaji.putIfAbsent(romaji, kana);
        if (other != null && !other.equals(kana)) {
            throw new IllegalStateException(romaji + " is both " + other + " and " + kana);
        }
    }
}
