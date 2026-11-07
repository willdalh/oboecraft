package io.github.willdalh.oboecraft.tools.readingclips;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The text VOICEVOX is given for a Japanese name. VOICEVOX reads kanji with its own dictionary, which can be wrong for
 * the name, like 間 read あいだ in 試練の間, and reads some kana wrongly too, like は after の as the particle わ, or the
 * katakana リョ after ン as リ and ヨ. So the tool tries these texts in order, and uses the first one whose moras VOICEVOX
 * says as the reviewed reading:
 *
 * <ol>
 *   <li>the name as it is written, like 試練の間, since the words give VOICEVOX their pitch accent,
 *   <li>the name with each part that isn't kana replaced by its reading in hiragana, like しれんのま, so katakana words
 *       stay words, as in しんしゃのハーフブロック,
 *   <li>the same with the readings in katakana, like キイロのポプラのハ, where a hiragana は would be the particle,
 *   <li>the whole reading in hiragana.
 * </ol>
 *
 * <p>If VOICEVOX says none of them as the reading, the second is used, and the clip is noted as unchecked.
 *
 * @param text     what VOICEVOX is given
 * @param nameSaid how VOICEVOX says the name as it is written, in katakana
 * @param checked  whether VOICEVOX says {@code text} as the reading
 */
record SpokenText(String text, String nameSaid, boolean checked) {
    /** How VOICEVOX says a text: its moras, each in katakana, like キョ. */
    interface Pronouncer {
        List<String> moras(String text);
    }

    /** Small kana that make one mora with the kana before them. っ is a mora of its own. */
    private static final String SMALL_KANA = "ァィゥェォャュョヮ";
    /** Small vowels, which VOICEVOX says as a vowel of their own after kana it has no mora for, like クォ as ク and オ. */
    private static final String SMALL_VOWELS = "ァィゥェォ";
    /**
     * The kana with a small vowel that VOICEVOX 0.26.1 says as one mora, found by asking it for each. VOICEVOX saying
     * one of these as two moras, like ディ as デ and イ, says another sound.
     */
    private static final Set<String> MORAS_WITH_SMALL_VOWEL = Set.of("キェ", "ギェ", "シェ", "スィ", "ジェ", "ズィ",
            "チェ", "ツァ", "ツィ", "ツェ", "ツォ", "ティ", "トゥ", "ディ", "ドゥ", "ニェ", "ヒェ", "ファ", "フィ", "フェ", "フォ",
            "ビェ", "ピェ", "ミェ", "リェ", "ヴァ", "ヴィ", "ヴェ", "ヴォ", "ウィ", "ウェ", "ウォ", "イェ");
    /** Kana that VOICEVOX writes as another kana with the same sound. */
    private static final Map<String, String> SAME_SOUND = Map.of("ヲ", "オ", "ヂ", "ジ", "ヅ", "ズ");
    private static final String LONG_VOWEL_MARK = "ー";
    /** The kana of each vowel, which VOICEVOX writes a long vowel as. */
    private static final Map<Character, String> VOWEL_KANA =
            Map.of('a', "ア", 'i', "イ", 'u', "ウ", 'e', "エ", 'o', "オ");

    SpokenText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(nameSaid, "nameSaid");
    }

    static SpokenText of(JapaneseName name, Pronouncer pronouncer) {
        List<String> reading = moras(katakana(name.reading()));
        List<String> nameMoras = pronouncer.moras(name.text());
        String nameSaid = String.join("", nameMoras);
        if (says(nameMoras, reading)) {
            return new SpokenText(name.text(), nameSaid, true);
        }
        String withHiraganaReadings = withReadings(name, Function.identity());
        Set<String> fallbacks = new LinkedHashSet<>(
                List.of(withHiraganaReadings, withReadings(name, SpokenText::katakana), name.reading()));
        fallbacks.remove(name.text());
        for (String fallback : fallbacks) {
            if (says(pronouncer.moras(fallback), reading)) {
                return new SpokenText(fallback, nameSaid, true);
            }
        }
        return new SpokenText(withHiraganaReadings, nameSaid, false);
    }

    /** Whether the text is the name as it is written, not made from its reading. */
    boolean isName(JapaneseName name) {
        return text.equals(name.text());
    }

    /** The name with each part that isn't kana replaced by its reading, as {@code script} writes the reading. */
    private static String withReadings(JapaneseName name, Function<String, String> script) {
        return name.parts().stream()
                .map(part -> isKana(part) ? part.text() : script.apply(part.reading()))
                .collect(Collectors.joining());
    }

    private static boolean isKana(NamePart part) {
        return JapaneseText.normalize(part.text()).equals(part.reading());
    }

    /**
     * Whether VOICEVOX's moras say the reading's moras. Each mora must be the same kana, except where VOICEVOX writes the
     * same sound another way: a long vowel as the vowel it lengthens, so ー after a mora in o, or ウ after one, may be
     * オ, and イ after a mora in e may be エ; ヲ, ヂ and ヅ as オ, ジ and ズ; and a kana with a small vowel that VOICEVOX
     * has no mora for, like クォ, as the kana and the vowel, ク and オ.
     */
    static boolean says(List<String> said, List<String> reading) {
        int at = 0;
        char vowelBefore = 0;
        for (String expected : reading) {
            if (at >= said.size()) {
                return false;
            }
            String mora = said.get(at);
            boolean longVowel = expected.equals(LONG_VOWEL_MARK)
                    || expected.equals("ウ") && vowelBefore == 'o'
                    || expected.equals("イ") && vowelBefore == 'e';
            boolean same = mora.equals(expected)
                    || mora.equals(SAME_SOUND.get(expected))
                    || longVowel && mora.equals(VOWEL_KANA.get(vowelBefore));
            if (same) {
                at++;
            } else if (saidAsKanaAndVowel(expected, said, at)) {
                at += 2;
            } else {
                return false;
            }
            if (!expected.equals(LONG_VOWEL_MARK)) {
                vowelBefore = JapaneseText.vowelOf(hiragana(expected.charAt(expected.length() - 1)));
            }
        }
        return at == said.size();
    }

    /**
     * Whether a mora like クォ, which VOICEVOX has no mora for, is said as its kana and its small vowel, ク and オ, at
     * {@code at}.
     */
    private static boolean saidAsKanaAndVowel(String expected, List<String> said, int at) {
        if (expected.length() != 2 || SMALL_VOWELS.indexOf(expected.charAt(1)) < 0
                || MORAS_WITH_SMALL_VOWEL.contains(expected) || at + 1 >= said.size()) {
            return false;
        }
        String vowel = VOWEL_KANA.get(JapaneseText.vowelOf(hiragana(expected.charAt(1))));
        return said.get(at).equals(expected.substring(0, 1)) && said.get(at + 1).equals(vowel);
    }

    /** A reading in katakana split into moras, like キョ, ウ, リョ and ク for キョウリョク. */
    static List<String> moras(String katakana) {
        List<String> moras = new ArrayList<>();
        for (int i = 0; i < katakana.length(); i++) {
            char kana = katakana.charAt(i);
            if (SMALL_KANA.indexOf(kana) >= 0 && !moras.isEmpty()) {
                moras.set(moras.size() - 1, moras.getLast() + kana);
            } else {
                moras.add(String.valueOf(kana));
            }
        }
        return moras;
    }

    private static char hiragana(char katakana) {
        return (char) (katakana >= 'ァ' && katakana <= 'ヶ' ? katakana - ('ァ' - 'ぁ') : katakana);
    }

    /** Hiragana as katakana. Other characters, like ー, stay as they are. */
    static String katakana(String hiragana) {
        StringBuilder text = new StringBuilder(hiragana.length());
        hiragana.chars().forEach(c -> text.append((char) (c >= 'ぁ' && c <= 'ゖ' ? c + ('ァ' - 'ぁ') : c)));
        return text.toString();
    }
}
