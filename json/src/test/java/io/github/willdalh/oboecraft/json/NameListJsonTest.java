package io.github.willdalh.oboecraft.json;

import io.github.willdalh.oboecraft.core.item.PlainName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import java.util.List;
import org.junit.jupiter.api.Test;

class NameListJsonTest {
    private static final List<NameListEntry> ENTRIES = List.of(
            new NameListEntry(new ItemId("block.minecraft.oak_log"), List.of("block.minecraft.oak_log"),
                    new JapaneseName(List.of(NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"),
                            new NamePart("木", "ぼく")))),
            new NameListEntry(new ItemId("block.minecraft.player_head"),
                    List.of("block.minecraft.player_head", "block.minecraft.player_head.named"),
                    new JapaneseName(List.of(NamePart.kana("プレイヤー"), NamePart.kana("の"),
                            new NamePart("頭", "あたま")))));

    private static final String FILE = """
            [
              {"id": "block.minecraft.oak_log", "translationKeys": ["block.minecraft.oak_log"], \
            "name": ["オーク", "の", ["原", "げん"], ["木", "ぼく"]]},
              {"id": "block.minecraft.player_head", "translationKeys": ["block.minecraft.player_head", \
            "block.minecraft.player_head.named"], "name": ["プレイヤー", "の", ["頭", "あたま"]]}
            ]
            """;

    @Test
    void writesOneEntryPerLineWithKanaPartsAsStrings() {
        assertEquals(FILE, NameListJson.format(ENTRIES));
    }

    @Test
    void readsWhatItWrites() {
        assertEquals(ENTRIES, NameListJson.parse(JsonParser.parseString(FILE)).entries());
    }

    @Test void plainNamesAndReviewedCorrectionsRoundTrip() {
        var corrected = new NameListEntry(new ItemId("block.minecraft.oak_log"), List.of("block.minecraft.oak_log"),
                new PlainName("Eichenstamm", "Broken translation"));
        String text = NameListJson.format(List.of(corrected));
        assertEquals(List.of(corrected), NameListJson.parse(JsonParser.parseString(text)).entries());
        assertTrue(text.contains("\"sourceName\": \"Broken translation\""));
        assertRefusedNaming("[{'id':'empty','translationKeys':['empty'],'name':''}]", "empty");
    }

    @Test
    void twoEntriesWithTheSameIdAreRefused() {
        List<NameListEntry> twice = List.of(ENTRIES.getFirst(), ENTRIES.getFirst());

        assertThrows(IllegalArgumentException.class, () -> NameListJson.format(twice));
    }

    /**
     * Parses a name list that should be refused, and checks that the error says which entry is wrong. The JSON is
     * written with single quotes, so it reads more easily here.
     */
    private static void assertRefusedNaming(String json, String expectedInMessage) {
        String nameList = json.replace('\'', '"');
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NameListJson.parse(JsonParser.parseString(nameList)), nameList);
        assertTrue(error.getMessage().contains(expectedInMessage),
                "the error \"" + error.getMessage() + "\" names " + expectedInMessage);
    }

    @Test
    void anInvalidEntryIsRefusedWithItsId() {
        String oakLog = "{'id': 'block.minecraft.oak_log', 'translationKeys': ['block.minecraft.oak_log'], ";
        String id = "block.minecraft.oak_log";

        assertRefusedNaming("[" + oakLog + "'name': [['原', 'ゲン']]}]", id);
        assertRefusedNaming("[" + oakLog + "'name': [['原']]}]", id);
        assertRefusedNaming("[" + oakLog + "'name': []}]", id);
        assertRefusedNaming("[" + oakLog + "'name': 42}]", id);
        assertRefusedNaming("[" + oakLog + "'name': [{'text': '原'}]}]", id);
        assertRefusedNaming("[" + oakLog + "'reading': 'おーくのげんぼく'}]", id);
        assertRefusedNaming("[{'id': 'block.minecraft.oak_log', 'translationKeys': 'block.minecraft.oak_log', "
                + "'name': [['原', 'げん']]}]", id);
    }

    @Test
    void anEntryWithoutAnIdIsRefusedWithItsPlace() {
        String dirt = "{'id': 'block.minecraft.dirt', 'translationKeys': ['block.minecraft.dirt'], "
                + "'name': [['土', 'つち']]}";
        String place = "entry 1 without an id";

        assertRefusedNaming("[" + dirt + ", {'translationKeys': ['block.minecraft.sand']}]", place);
        assertRefusedNaming("[" + dirt + ", 'block.minecraft.sand']", place);
    }
}
