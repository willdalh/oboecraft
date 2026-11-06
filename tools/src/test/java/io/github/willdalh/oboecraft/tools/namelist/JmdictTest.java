package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JmdictTest {
    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE JMdict [
            <!ELEMENT JMdict (entry*)>
            <!ENTITY n "noun (common) (futsuumeishi)">
            ]>
            <!-- JMdict created: 2026-11-06 -->
            <JMdict>
            <entry>
            <ent_seq>1</ent_seq>
            <k_ele><keb>原木</keb></k_ele>
            <r_ele><reb>げんぼく</reb></r_ele>
            <sense><pos>&n;</pos><gloss>raw wood</gloss></sense>
            </entry>
            <entry>
            <ent_seq>2</ent_seq>
            <k_ele><keb>角</keb></k_ele>
            <k_ele><keb>稜</keb></k_ele>
            <r_ele><reb>かど</reb></r_ele>
            <r_ele><reb>つの</reb><re_restr>角</re_restr></r_ele>
            <r_ele><reb>カド</reb><re_nokanji/></r_ele>
            <sense><pos>&n;</pos><gloss>corner</gloss></sense>
            </entry>
            </JMdict>
            """;

    private static Jmdict read() throws IOException {
        return Jmdict.read(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aWordHasTheReadingsOfItsEntry() throws IOException {
        assertEquals(Set.of("げんぼく"), read().readingsOf("原木"));
    }

    @Test
    void aReadingLimitedToSomeSpellingsOnlyBelongsToThem() throws IOException {
        Jmdict jmdict = read();

        assertEquals(Set.of("かど", "つの"), jmdict.readingsOf("角"));
        assertEquals(Set.of("かど"), jmdict.readingsOf("稜"));
    }

    @Test
    void aWordJmdictDoesntHaveHasNoReadings() throws IOException {
        assertEquals(Set.of(), read().readingsOf("作業台"));
    }

    @Test
    void theDateTheFileWasMadeIsRead() throws IOException {
        assertEquals("2026-11-06", read().created());
    }
}
