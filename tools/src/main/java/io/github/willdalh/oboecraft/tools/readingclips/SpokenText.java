package io.github.willdalh.oboecraft.tools.readingclips;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The text VOICEVOX is given for a Japanese name. The name as it is written gives VOICEVOX the words, so it says them
 * with their pitch accent. But VOICEVOX reads kanji with its own dictionary, which can be wrong for the name, like 間
 * read あいだ in 試練の間. So the name is only used when VOICEVOX says the reviewed reading for it. Otherwise it is given
 * the reading in hiragana. Hiragana, not katakana, since VOICEVOX splits some katakana wrongly, like リョ after ン as
 * リ and ヨ. If VOICEVOX doesn't say the reading for that either, the clip is said from the reading anyway and noted.
 *
 * @param text        what VOICEVOX is given
 * @param fromReading whether it is the reading, since VOICEVOX would read the name otherwise
 * @param nameSaid    how VOICEVOX says the name as it is written, in katakana
 * @param checked     whether VOICEVOX says the reading for {@code text}
 */
record SpokenText(String text, boolean fromReading, String nameSaid, boolean checked) {
    /** How VOICEVOX says a text: its moras, each in katakana, like キョ. */
    interface Pronouncer {
        List<String> moras(String text);
    }

    /** Small kana that make one mora with the kana before them. っ is a mora of its own. */
    private static final String SMALL_KANA = "ァィゥェォャュョヮ";
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
            return new SpokenText(name.text(), false, nameSaid, true);
        }
        boolean checked = says(pronouncer.moras(name.reading()), reading);
        return new SpokenText(name.reading(), true, nameSaid, checked);
    }

    /**
     * Whether VOICEVOX's moras say the reading's moras. Each mora must be the same kana, except where VOICEVOX writes the
     * same sound another way: a long vowel as the vowel it lengthens, so ー after a mora in o, or ウ after one, may be
     * オ, and イ after a mora in e may be エ; and ヲ, ヂ and ヅ as オ, ジ and ズ.
     */
    static boolean says(List<String> said, List<String> reading) {
        if (said.size() != reading.size()) {
            return false;
        }
        char vowelBefore = 0;
        for (int i = 0; i < said.size(); i++) {
            String mora = said.get(i);
            String expected = reading.get(i);
            boolean longVowel = expected.equals(LONG_VOWEL_MARK)
                    || expected.equals("ウ") && vowelBefore == 'o'
                    || expected.equals("イ") && vowelBefore == 'e';
            boolean same = mora.equals(expected)
                    || mora.equals(SAME_SOUND.get(expected))
                    || longVowel && mora.equals(VOWEL_KANA.get(vowelBefore));
            if (!same) {
                return false;
            }
            if (!expected.equals(LONG_VOWEL_MARK)) {
                vowelBefore = JapaneseText.vowelOf(hiragana(expected.charAt(expected.length() - 1)));
            }
        }
        return true;
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
