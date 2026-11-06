package io.github.willdalh.oboecraft.json;

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
            "japaneseName": ["オーク", "の", ["原", "げん"], ["木", "ぼく"]]},
              {"id": "block.minecraft.player_head", "translationKeys": ["block.minecraft.player_head", \
            "block.minecraft.player_head.named"], "japaneseName": ["プレイヤー", "の", ["頭", "あたま"]]}
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

        assertRefusedNaming("[" + oakLog + "'japaneseName': [['原', 'ゲン']]}]", id);
        assertRefusedNaming("[" + oakLog + "'japaneseName': [['原']]}]", id);
        assertRefusedNaming("[" + oakLog + "'japaneseName': []}]", id);
        assertRefusedNaming("[" + oakLog + "'japaneseName': 'オークの原木'}]", id);
        assertRefusedNaming("[" + oakLog + "'japaneseName': [{'text': '原'}]}]", id);
        assertRefusedNaming("[" + oakLog + "'reading': 'おーくのげんぼく'}]", id);
        assertRefusedNaming("[{'id': 'block.minecraft.oak_log', 'translationKeys': 'block.minecraft.oak_log', "
                + "'japaneseName': [['原', 'げん']]}]", id);
    }

    @Test
    void anEntryWithoutAnIdIsRefusedWithItsPlace() {
        String dirt = "{'id': 'block.minecraft.dirt', 'translationKeys': ['block.minecraft.dirt'], "
                + "'japaneseName': [['土', 'つち']]}";
        String place = "entry 1 without an id";

        assertRefusedNaming("[" + dirt + ", {'translationKeys': ['block.minecraft.sand']}]", place);
        assertRefusedNaming("[" + dirt + ", 'block.minecraft.sand']", place);
    }
}
