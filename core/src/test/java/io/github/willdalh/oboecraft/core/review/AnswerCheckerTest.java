package io.github.willdalh.oboecraft.core.review;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerCheckerTest {
    private static final NameListEntry OAK_LOG =
            entry(kana("オーク"), kana("の"), part("原", "げん"), part("木", "ぼく"));
    private static final NameListEntry CRAFTING_TABLE = entry(part("作", "さ"), part("業", "ぎょう"), part("台", "だい"));

    private static NamePart kana(String text) {
        return NamePart.kana(text);
    }

    private static NamePart part(String text, String reading) {
        return new NamePart(text, reading);
    }

    private static NameListEntry entry(NamePart... parts) {
        return new NameListEntry(new ItemId("test"), List.of("test"), new JapaneseName(List.of(parts)));
    }

    private static void assertPasses(NameListEntry entry, String... answers) {
        for (String answer : answers) {
            assertTrue(AnswerChecker.isCorrect(entry, answer), answer + " passes for " + entry.name());
        }
    }

    private static void assertFails(NameListEntry entry, String... answers) {
        for (String answer : answers) {
            assertFalse(AnswerChecker.isCorrect(entry, answer), answer + " fails for " + entry.name());
        }
    }

    @Test
    void theJapaneseNamePasses() {
        assertPasses(OAK_LOG, "オークの原木");
    }

    @Test
    void theReadingInHiraganaOrKatakanaPasses() {
        assertPasses(OAK_LOG, "おーくのげんぼく", "オークノゲンボク");
    }

    @Test
    void kanjiAndKanaCanBeMixedPerKanji() {
        assertPasses(OAK_LOG, "オークの原ぼく", "オークのげん木", "おーくの原木");
    }

    @Test
    void theParticleNoIsOptional() {
        assertPasses(OAK_LOG, "オーク原木", "ooku genboku");
    }

    @Test
    void romajiPasses() {
        assertPasses(OAK_LOG, "ooku no genboku", "oukunogenboku", "OOKU NO GENBOKU");
    }

    @Test
    void romajiKanaAndKanjiCanBeMixed() {
        assertPasses(OAK_LOG, "ookuの原木", "オークno原boku");
    }

    @Test
    void aLongVowelCanBeWrittenAnotherWay() {
        assertPasses(OAK_LOG, "おおくのげんぼく", "おうくのげんぼく", "ōku no genboku", "o-ku no genboku");
        assertPasses(CRAFTING_TABLE, "さぎょうだい", "さぎょーだい", "さぎょおだい", "sagyoudai", "sagyoodai", "sagyōdai");
    }

    @Test
    void aLongEIsWrittenWithAnEOrAnI() {
        NameListEntry movie = entry(part("映", "えい"), part("画", "が"));

        assertPasses(movie, "えいが", "ええが", "えーが", "eiga", "eega", "ēga");
    }

    @Test
    void aVowelThatStartsAPartIsntALongVowel() {
        NameListEntry retail = entry(part("小", "こ"), part("売", "うり"));
        NameListEntry care = entry(part("手", "て"), part("入", "い"), kana("れ"));

        assertPasses(retail, "kouri", "こうり", "小売");
        assertFails(retail, "koori", "ko-ri", "こおり", "こーり");
        assertPasses(care, "teire", "ていれ");
        assertFails(care, "teere", "て-れ", "てーれ");
    }

    @Test
    void aLeftOutParticleDoesntMakeTheNextVowelLong() {
        NameListEntry netherDepths = entry(kana("ネザー"), kana("の"), part("奥", "おく"));

        assertPasses(netherDepths, "neza- no oku", "neza-oku", "ネザー奥");
        assertFails(netherDepths, "neza-uku", "nezaa-ku");
    }

    @Test
    void aMissingLongVowelFails() {
        assertFails(OAK_LOG, "おくのげんぼく", "oku no genboku");
        assertFails(CRAFTING_TABLE, "さぎょだい", "sagyodai");
    }

    @Test
    void nCanBeWrittenNOrNnOrNApostrophe() {
        assertPasses(OAK_LOG, "ooku no genboku", "ooku no gennboku", "ooku no gen'boku");
    }

    @Test
    void anNBeforeAVowelStartsTheNextSyllable() {
        NameListEntry memorial = entry(part("記", "き"), part("念", "ねん"));
        NameListEntry noSmoking = entry(part("禁", "きん"), part("煙", "えん"));

        assertPasses(memorial, "kinen");
        assertFails(memorial, "kinnen");
        assertPasses(noSmoking, "kinnen", "kin'en");
        assertFails(noSmoking, "kinen");
    }

    @Test
    void anNBeforeAYStartsTheNextSyllable() {
        NameListEntry tonight = entry(part("今", "こん"), part("夜", "や"));

        assertPasses(tonight, "konnya", "kon'ya");
        assertFails(tonight, "konya");
    }

    @Test
    void anNBeforeAnotherNCanBeN() {
        NameListEntry woman = entry(part("女", "おんな"));

        assertPasses(woman, "onna", "onnna", "on'na");
    }

    @Test
    void bothRomajiStylesPass() {
        assertPasses(entry(kana("シラカバ")), "shirakaba", "sirakaba");
        assertPasses(entry(part("土", "つち")), "tsuchi", "tuchi", "tuti", "tsuti");
        assertPasses(entry(part("船", "ふね")), "fune", "hune");
        assertPasses(entry(part("地", "じ"), part("面", "めん")), "jimen", "zimen");
        assertPasses(entry(part("写", "しゃ"), part("真", "しん")), "shashin", "syasin");
        assertPasses(entry(part("砂利", "じゃり")), "jari", "zyari", "jyari");
    }

    @Test
    void aMissingDakutenFails() {
        NameListEntry bucket = entry(kana("バケツ"));

        assertPasses(bucket, "ばけつ", "baketsu");
        assertFails(bucket, "はけつ", "haketsu");
    }

    @Test
    void aBigKanaForASmallOneFails() {
        NameListEntry guest = entry(part("客", "きゃく"));

        assertPasses(guest, "きゃく", "kyaku");
        assertFails(guest, "きやく", "kiyaku");
    }

    @Test
    void aSmallTsuIsADoubledConsonant() {
        NameListEntry onePiece = entry(part("一", "いっ"), part("本", "ぽん"));
        NameListEntry matcha = entry(part("抹", "まっ"), part("茶", "ちゃ"));

        assertPasses(onePiece, "いっぽん", "いっ本", "ippon", "ixtupon", "iltsupon");
        assertPasses(matcha, "matcha", "maccha", "mattya");
    }

    @Test
    void aMissingSmallTsuFails() {
        NameListEntry onePiece = entry(part("一", "いっ"), part("本", "ぽん"));

        assertFails(onePiece, "いぽん", "ipon", "i本");
    }

    @Test
    void kanaWithTheSameSoundFail() {
        NameListEntry crescent = entry(part("三日月", "みかづき"));
        NameListEntry nosebleed = entry(part("鼻", "はな"), part("血", "ぢ"));
        NameListEntry drinkWater = entry(part("水", "みず"), kana("を"), part("飲", "の"), kana("む"));

        assertPasses(crescent, "みかづき", "mikaduki");
        assertFails(crescent, "みかずき", "mikazuki");
        assertPasses(nosebleed, "はなぢ", "hanadi");
        assertFails(nosebleed, "はなじ", "hanaji");
        assertPasses(drinkWater, "水を飲む", "mizuwonomu");
        assertFails(drinkWater, "水お飲む", "mizuonomu");
    }

    @Test
    void wrongKanjiWithTheSameReadingFails() {
        assertFails(OAK_LOG, "オークの減木", "オークの原目");
    }

    @Test
    void kanjiReadTogetherAreWrittenTogether() {
        NameListEntry today = entry(part("今日", "きょう"));

        assertPasses(today, "今日", "きょう", "kyou", "kyō");
        assertFails(today, "今ょう", "きょ日");
    }

    @Test
    void kanaForAKanjiMustBeItsReading() {
        assertFails(OAK_LOG, "オークのはらき", "オークの原き");
    }

    @Test
    void lettersInTheNameCanBeTypedAsTheyAre() {
        NameListEntry tnt = entry(part("TNT", "てぃーえぬてぃー"));

        assertPasses(tnt, "TNT", "tnt", "ＴＮＴ", "てぃーえぬてぃー", "thiienuthii");
        assertFails(tnt, "TN");
    }

    @Test
    void spacesCaseAndPunctuationAreIgnored() {
        NameListEntry jackOLantern = entry(kana("ジャック・オ・ランタン"));

        assertPasses(jackOLantern, "ジャックオランタン", "jakku o rantan", "Jakku O Rantan");
        assertPasses(OAK_LOG, " オークの原木　", "オーク・の・原木", "ｵｰｸの原木");
    }

    @Test
    void theParticleIsOptionalWhenTheNameWritesItInKatakana() {
        assertPasses(entry(kana("オーク"), kana("ノ"), part("原", "げん")), "オーク原", "オークの原", "ooku gen");
    }

    @Test
    void noIsOnlyOptionalAsTheParticle() {
        assertFails(entry(kana("キノコ")), "キコ", "kiko");
    }

    @Test
    void anythingElseFails() {
        assertFails(OAK_LOG, "オーク", "原木", "オークの原木の", "ooku no genboku no", "・");
        assertFails(CRAFTING_TABLE, "作業の台");
    }
}
