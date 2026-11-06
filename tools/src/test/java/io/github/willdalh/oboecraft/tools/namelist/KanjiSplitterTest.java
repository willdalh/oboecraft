package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.NamePart;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KanjiSplitterTest {
    private final KanjiSplitter splitter = new KanjiSplitter(Kanjidic.of(Map.ofEntries(
            Map.entry("原", Set.of("げん", "はら")),
            Map.entry("木", Set.of("ぼく", "もく", "き", "こ")),
            Map.entry("作", Set.of("さく", "さ", "つく")),
            Map.entry("業", Set.of("ぎょう", "ごう", "わざ")),
            Map.entry("台", Set.of("だい", "たい")),
            Map.entry("松", Set.of("しょう", "まつ")),
            Map.entry("明", Set.of("めい", "みょう", "あ", "あか")),
            Map.entry("焼", Set.of("しょう", "や", "やく", "やき")),
            Map.entry("鳥", Set.of("ちょう", "とり")),
            Map.entry("角", Set.of("かく", "かど", "つの")),
            Map.entry("笛", Set.of("てき", "ふえ")),
            Map.entry("一", Set.of("いち", "いつ", "ひと")),
            Map.entry("本", Set.of("ほん", "もと")),
            Map.entry("様", Set.of("よう", "さま")),
            Map.entry("三", Set.of("さん", "み")),
            Map.entry("月", Set.of("げつ", "がつ", "つき")),
            Map.entry("硫", Set.of("りゅう")),
            Map.entry("黄", Set.of("おう", "こう", "き")))));

    private static List<NamePart> parts(String... textsAndReadings) {
        List<NamePart> parts = new java.util.ArrayList<>();
        for (int i = 0; i < textsAndReadings.length; i += 2) {
            parts.add(new NamePart(textsAndReadings[i], textsAndReadings[i + 1]));
        }
        return parts;
    }

    @Test
    void eachKanjiGetsItsOwnReading() {
        KanjiSplitter.Split split = splitter.split("原木", "げんぼく");

        assertEquals(parts("原", "げん", "木", "ぼく"), split.parts());
        assertTrue(split.notes().isEmpty(), split.notes().toString());
        assertEquals(parts("作", "さ", "業", "ぎょう", "台", "だい"), splitter.split("作業台", "さぎょうだい").parts());
    }

    @Test
    void kanaInTheWordIsItsOwnPart() {
        assertEquals(parts("焼", "や", "き", "き", "鳥", "とり"), splitter.split("焼き鳥", "やきとり").parts());
    }

    @Test
    void aVerbStemIsReadWhenTheOkuriganaIsntWritten() {
        assertEquals(parts("焼", "やき", "鳥", "とり"), splitter.split("焼鳥", "やきとり").parts());
    }

    @Test
    void aReadingCanGetADakutenAfterTheFirstKanji() {
        KanjiSplitter.Split split = splitter.split("角笛", "つのぶえ");

        assertEquals(parts("角", "つの", "笛", "ぶえ"), split.parts());
        assertTrue(split.notes().isEmpty(), split.notes().toString());
    }

    @Test
    void aReadingCanEndInASmallTsuAndTheNextGetAHandakuten() {
        assertEquals(parts("一", "いっ", "本", "ぽん"), splitter.split("一本", "いっぽん").parts());
    }

    @Test
    void theRepeatMarkIsReadLikeTheKanjiBeforeIt() {
        assertEquals(parts("様", "さま", "々", "ざま"), splitter.split("様々", "さまざま").parts());
    }

    @Test
    void aCounterGetsItsReading() {
        assertEquals(parts("三", "さん", "ヶ", "か", "月", "げつ"), splitter.split("三ヶ月", "さんかげつ").parts());
    }

    @Test
    void kanjiWhoseReadingsDontAddUpAreReadTogether() {
        KanjiSplitter.Split split = splitter.split("松明", "たいまつ");

        assertEquals(parts("松明", "たいまつ"), split.parts());
        assertEquals(List.of("松明 is read together as たいまつ"), split.notes());
    }

    @Test
    void aKanjiWithAReadingKanjidicDoesntHaveIsReadTogetherWithTheKanjiNextToIt() {
        KanjiSplitter.Split split = splitter.split("硫黄", "いおう");

        assertEquals(parts("硫黄", "いおう"), split.parts());
        assertEquals(List.of("硫黄 is read together as いおう"), split.notes());
    }

    @Test
    void aSingleKanjiWithAReadingKanjidicDoesntHaveIsNoted() {
        KanjiSplitter.Split split = splitter.split("野", "や");

        assertEquals(parts("野", "や"), split.parts());
        assertEquals(List.of("野 is read や, which KANJIDIC2 doesn't have"), split.notes());
    }

    @Test
    void aReadingThatDoesntFitTheKanaIsntSplit() {
        KanjiSplitter.Split split = splitter.split("焼き", "やけ");

        assertEquals(parts("焼き", "やけ"), split.parts());
        assertFalse(split.notes().isEmpty());
    }
}
