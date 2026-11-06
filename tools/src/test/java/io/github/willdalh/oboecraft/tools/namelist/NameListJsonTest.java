package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(ENTRIES, NameListJson.parse(JsonParser.parseString(FILE)));
    }

    @Test
    void twoEntriesWithTheSameIdAreRefused() {
        List<NameListEntry> twice = List.of(ENTRIES.getFirst(), ENTRIES.getFirst());

        assertThrows(IllegalArgumentException.class, () -> NameListJson.format(twice));
    }
}
