package io.github.willdalh.oboecraft.tools.readingclips;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The text VOICEVOX is given for a Japanese name. The name as it is written gives VOICEVOX the words, so it says them
 * with their pitch accent. But VOICEVOX reads kanji with its own dictionary, which can be wrong for the name, like 間
 * read あいだ in 試練の間. So the name is only used when VOICEVOX says the same sounds as the reviewed reading. Otherwise
 * it is given the reading in katakana, which it can only say one way.
 *
 * @param text        what VOICEVOX is given
 * @param fromReading whether it is the reading, since VOICEVOX would read the name otherwise
 * @param nameSaid    how VOICEVOX says the name as it is written, in katakana
 */
record SpokenText(String text, boolean fromReading, String nameSaid) {
    private static final Set<String> VOWELS = Set.of("a", "i", "u", "e", "o");
    private static final Set<String> DEVOICED_VOWELS = Set.of("A", "I", "U", "E", "O");

    /** How VOICEVOX says a text. */
    interface Pronouncer {
        List<Mora> moras(String text);
    }

    SpokenText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(nameSaid, "nameSaid");
    }

    static SpokenText of(JapaneseName name, Pronouncer pronouncer) {
        String reading = katakana(name.reading());
        List<Mora> nameMoras = pronouncer.moras(name.text());
        String nameSaid = nameMoras.stream().map(Mora::text).collect(Collectors.joining());
        boolean sameSounds = sounds(nameMoras).equals(sounds(pronouncer.moras(reading)));
        return sameSounds ? new SpokenText(name.text(), false, nameSaid) : new SpokenText(reading, true, nameSaid);
    }

    /**
     * The sounds of the moras, one per mora, as its consonant and vowel. Devoiced vowels count as voiced. VOICEVOX
     * writes a long vowel either way, like こう as コオ or コウ, so a long vowel counts as the vowel before it: the same
     * vowel, う after an o, or い after an e.
     */
    static List<String> sounds(List<Mora> moras) {
        List<String> sounds = new ArrayList<>(moras.size());
        String vowelBefore = "";
        for (Mora mora : moras) {
            String vowel = DEVOICED_VOWELS.contains(mora.vowel()) ? mora.vowel().toLowerCase() : mora.vowel();
            if (mora.consonant().isEmpty() && isLongVowel(vowelBefore, vowel)) {
                vowel = vowelBefore;
            }
            sounds.add(mora.consonant().orElse("") + ":" + vowel);
            vowelBefore = VOWELS.contains(vowel) ? vowel : "";
        }
        return sounds;
    }

    private static boolean isLongVowel(String vowelBefore, String vowel) {
        return !vowelBefore.isEmpty()
                && (vowel.equals(vowelBefore)
                        || vowelBefore.equals("o") && vowel.equals("u")
                        || vowelBefore.equals("e") && vowel.equals("i"));
    }

    /** Hiragana as katakana. Other characters, like ー, stay as they are. */
    static String katakana(String hiragana) {
        StringBuilder text = new StringBuilder(hiragana.length());
        hiragana.chars().forEach(c -> text.append((char) (c >= 'ぁ' && c <= 'ゖ' ? c + ('ァ' - 'ぁ') : c)));
        return text.toString();
    }
}
