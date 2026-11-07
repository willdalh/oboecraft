package io.github.willdalh.oboecraft.tools.readingclips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NamePart;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpokenTextTest {
    private static Mora mora(String text, String consonant, String vowel) {
        return new Mora(text, Optional.ofNullable(consonant), vowel);
    }

    /** Says each text as the moras given for it, like VOICEVOX would. */
    private static SpokenText.Pronouncer pronouncer(Map<String, List<Mora>> moras) {
        return text -> {
            List<Mora> said = moras.get(text);
            if (said == null) {
                throw new AssertionError("Unexpected text " + text);
            }
            return said;
        };
    }

    private static final JapaneseName OAK_LOG = new JapaneseName(List.of(
            NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく")));

    @Test
    void theNameIsSpokenWhenVoicevoxSaysItLikeTheReading() {
        // VOICEVOX writes the long vowel of オーク as オ.
        List<Mora> oakLog = List.of(mora("オ", null, "o"), mora("オ", null, "o"), mora("ク", "k", "U"),
                mora("ノ", "n", "o"), mora("ゲ", "g", "e"), mora("ン", null, "N"), mora("ボ", "b", "o"),
                mora("ク", "k", "u"));
        List<Mora> reading = List.of(mora("オ", null, "o"), mora("ー", null, "o"), mora("ク", "k", "u"),
                mora("ノ", "n", "o"), mora("ゲ", "g", "e"), mora("ン", null, "N"), mora("ボ", "b", "o"),
                mora("ク", "k", "u"));

        SpokenText spoken = SpokenText.of(OAK_LOG, pronouncer(Map.of("オークの原木", oakLog, "オークノゲンボク", reading)));

        assertEquals("オークの原木", spoken.text());
        assertFalse(spoken.fromReading());
        assertEquals("オオクノゲンボク", spoken.nameSaid());
    }

    @Test
    void theReadingIsSpokenWhenVoicevoxReadsAKanjiDifferently() {
        JapaneseName trialChamber = new JapaneseName(List.of(
                new NamePart("試", "し"), new NamePart("練", "れん"), NamePart.kana("の"), new NamePart("間", "ま")));
        List<Mora> trial = List.of(mora("シ", "sh", "i"), mora("レ", "r", "e"), mora("ン", null, "N"),
                mora("ノ", "n", "o"));
        List<Mora> aida = List.of(mora("ア", null, "a"), mora("イ", null, "i"), mora("ダ", "d", "a"));
        List<Mora> ma = List.of(mora("マ", "m", "a"));

        SpokenText spoken = SpokenText.of(trialChamber, pronouncer(Map.of(
                "試練の間", concat(trial, aida), "シレンノマ", concat(trial, ma))));

        assertEquals("シレンノマ", spoken.text());
        assertTrue(spoken.fromReading());
        assertEquals("シレンノアイダ", spoken.nameSaid());
    }

    @Test
    void aLongVowelCountsAsTheVowelBeforeIt() {
        List<Mora> kouAsKoo = List.of(mora("コ", "k", "o"), mora("オ", null, "o"));
        List<Mora> kouAsKou = List.of(mora("コ", "k", "o"), mora("ウ", null, "u"));
        List<Mora> keiAsKee = List.of(mora("ケ", "k", "e"), mora("エ", null, "e"));
        List<Mora> keiAsKei = List.of(mora("ケ", "k", "e"), mora("イ", null, "i"));

        assertEquals(SpokenText.sounds(kouAsKoo), SpokenText.sounds(kouAsKou));
        assertEquals(SpokenText.sounds(keiAsKee), SpokenText.sounds(keiAsKei));
    }

    @Test
    void otherVowelsAfterAVowelAreTheirOwnSound() {
        List<Mora> ai = List.of(mora("ア", null, "a"), mora("イ", null, "i"));
        List<Mora> aa = List.of(mora("ア", null, "a"), mora("ア", null, "a"));
        List<Mora> ou = List.of(mora("オ", null, "o"), mora("ウ", "w", "u"));
        List<Mora> oo = List.of(mora("オ", null, "o"), mora("オ", null, "o"));

        assertFalse(SpokenText.sounds(ai).equals(SpokenText.sounds(aa)));
        // A vowel with a consonant is never a long vowel.
        assertFalse(SpokenText.sounds(ou).equals(SpokenText.sounds(oo)));
    }

    @Test
    void devoicedVowelsCountAsVoiced() {
        assertEquals(SpokenText.sounds(List.of(mora("ク", "k", "U"))), SpokenText.sounds(List.of(mora("ク", "k", "u"))));
    }

    @Test
    void nAndSmallTsuAreNotVowels() {
        // ン followed by ン isn't a long vowel, and neither is a vowel after ッ.
        List<Mora> nn = List.of(mora("ン", null, "N"), mora("ン", null, "N"));
        assertEquals(List.of(":N", ":N"), SpokenText.sounds(nn));
        assertEquals(List.of(":cl", ":o"), SpokenText.sounds(List.of(mora("ッ", null, "cl"), mora("オ", null, "o"))));
    }

    @Test
    void hiraganaBecomesKatakana() {
        assertEquals("オークノゲンボク", SpokenText.katakana("おーくのげんぼく"));
        assertEquals("ティーエヌティー", SpokenText.katakana("てぃーえぬてぃー"));
        assertEquals("ヴ", SpokenText.katakana("ゔ"));
    }

    private static List<Mora> concat(List<Mora> first, List<Mora> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
