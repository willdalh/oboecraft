package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KanjidicTest {
    private static Set<String> readingsOf(String written) {
        Set<String> readings = new LinkedHashSet<>();
        Kanjidic.addReading(written, readings);
        return readings;
    }

    @Test
    void anOnReadingIsInHiragana() {
        assertEquals(Set.of("げん"), readingsOf("ゲン"));
    }

    @Test
    void aKunReadingWithOkuriganaGivesItsStemTheWordAndTheStemForm() {
        assertEquals(Set.of("や", "やく", "やき"), readingsOf("や.く"));
        assertEquals(Set.of("ひか", "ひかる", "ひかり"), readingsOf("ひか.る"));
    }

    @Test
    void theDashOfAPrefixOrSuffixIsLeftOut() {
        assertEquals(Set.of("や", "やき"), readingsOf("-や.き"));
        assertEquals(Set.of("こ"), readingsOf("こ-"));
    }

    @Test
    void readsTheJapaneseReadingsOfEachKanji() throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE kanjidic2 [
                <!ELEMENT kanjidic2 (header,character*)>
                ]>
                <kanjidic2>
                <header><file_version>4</file_version><database_version>2026-310</database_version></header>
                <character>
                <literal>原</literal>
                <reading_meaning><rmgroup>
                <reading r_type="pinyin">yuan2</reading>
                <reading r_type="ja_on">ゲン</reading>
                <reading r_type="ja_kun">はら</reading>
                <meaning>meadow</meaning>
                </rmgroup><nanori>もと</nanori></reading_meaning>
                </character>
                </kanjidic2>
                """;

        Kanjidic kanjidic = Kanjidic.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Set.of("げん", "はら"), kanjidic.readingsOf("原".codePointAt(0)));
        assertEquals(Set.of(), kanjidic.readingsOf("木".codePointAt(0)));
        assertEquals("2026-310", kanjidic.version());
    }
}
