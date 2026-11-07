package io.github.willdalh.oboecraft.tools.readingclips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NamePart;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpokenTextTest {
    /** Says each text as the moras given for it, like VOICEVOX would. */
    private static SpokenText.Pronouncer pronouncer(Map<String, List<String>> moras) {
        return text -> {
            List<String> said = moras.get(text);
            if (said == null) {
                throw new AssertionError("Unexpected text " + text);
            }
            return said;
        };
    }

    private static final JapaneseName OAK_LOG = new JapaneseName(List.of(
            NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく")));

    private static final JapaneseName TRIAL_CHAMBER = new JapaneseName(List.of(
            new NamePart("試", "し"), new NamePart("練", "れん"), NamePart.kana("の"), new NamePart("間", "ま")));

    @Test
    void theNameIsSpokenWhenVoicevoxSaysTheReadingForIt() {
        // VOICEVOX writes the long vowel of オーク as オ.
        SpokenText spoken = SpokenText.of(OAK_LOG, pronouncer(Map.of(
                "オークの原木", List.of("オ", "オ", "ク", "ノ", "ゲ", "ン", "ボ", "ク"))));

        assertEquals(new SpokenText("オークの原木", false, "オオクノゲンボク", true), spoken);
    }

    @Test
    void theReadingInHiraganaIsSpokenWhenVoicevoxReadsAKanjiDifferently() {
        SpokenText spoken = SpokenText.of(TRIAL_CHAMBER, pronouncer(Map.of(
                "試練の間", List.of("シ", "レ", "ン", "ノ", "ア", "イ", "ダ"),
                "しれんのま", List.of("シ", "レ", "ン", "ノ", "マ"))));

        assertEquals(new SpokenText("しれんのま", true, "シレンノアイダ", true), spoken);
    }

    @Test
    void aReadingThatVoicevoxDoesntSayEitherIsNotChecked() {
        SpokenText spoken = SpokenText.of(TRIAL_CHAMBER, pronouncer(Map.of(
                "試練の間", List.of("シ", "レ", "ン", "ノ", "ア", "イ", "ダ"),
                "しれんのま", List.of("シ", "レ", "ン", "ノ", "マ", "ア"))));

        assertTrue(spoken.fromReading());
        assertFalse(spoken.checked());
    }

    @Test
    void smallKanaJoinTheKanaBeforeThem() {
        assertEquals(List.of("キョ", "ウ", "リョ", "ク"), SpokenText.moras("キョウリョク"));
        assertEquals(List.of("ティ", "ー", "エ", "ヌ"), SpokenText.moras("ティーエヌ"));
        assertEquals(List.of("ベ", "ッ", "ド"), SpokenText.moras("ベッド"));
    }

    @Test
    void aSplitSmallKanaIsNotTheReading() {
        // VOICEVOX splits リョ after ン in katakana, which says another word.
        assertFalse(SpokenText.says(List.of("セ", "ン", "リ", "ヨ", "ク", "ガ", "ン"), SpokenText.moras("センリョクガン")));
        assertTrue(SpokenText.says(List.of("セ", "ン", "リョ", "ク", "ガ", "ン"), SpokenText.moras("センリョクガン")));
    }

    @Test
    void longVowelsMayBeWrittenAsTheVowelTheyLengthen() {
        assertTrue(SpokenText.says(List.of("キョ", "オ"), SpokenText.moras("キョウ")));
        assertTrue(SpokenText.says(List.of("ケ", "エ"), SpokenText.moras("ケイ")));
        assertTrue(SpokenText.says(List.of("テ", "エ"), SpokenText.moras("テー")));
        assertTrue(SpokenText.says(List.of("ティ", "イ", "エ", "ヌ"), SpokenText.moras("ティーエヌ")));
        assertFalse(SpokenText.says(List.of("テ", "イ"), SpokenText.moras("テー")));
        assertFalse(SpokenText.says(List.of("カ", "エ"), SpokenText.moras("カイ")));
        assertFalse(SpokenText.says(List.of("カ", "オ"), SpokenText.moras("カウ")));
    }

    @Test
    void kanaWithTheSameSoundMayBeWrittenAsTheOther() {
        assertTrue(SpokenText.says(List.of("ジュ", "ヒ", "オ"), SpokenText.moras("ジュヒヲ")));
        assertTrue(SpokenText.says(List.of("ミ", "カ", "ズ", "キ"), SpokenText.moras("ミカヅキ")));
        assertTrue(SpokenText.says(List.of("ハ", "ナ", "ジ"), SpokenText.moras("ハナヂ")));
        assertFalse(SpokenText.says(List.of("ネ", "ツ", "イ", "タ"), SpokenText.moras("ネヅイタ")));
    }

    @Test
    void hiraganaBecomesKatakana() {
        assertEquals("オークノゲンボク", SpokenText.katakana("おーくのげんぼく"));
        assertEquals("ティーエヌティー", SpokenText.katakana("てぃーえぬてぃー"));
        assertEquals("ヴ", SpokenText.katakana("ゔ"));
    }
}
