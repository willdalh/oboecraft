package io.github.willdalh.oboecraft.fabric.gametest;

import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.item.NameListFile;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The name list that ships with the mod, as the server loaded it from its file. */
public class NameListGameTest {
    private static Optional<JapaneseName> japaneseNameOf(String id) {
        NameList nameList = OboecraftMod.itemMapping().nameList();
        return nameList.byId(new ItemId(id)).map(NameListEntry::japaneseName);
    }

    @GameTest
    public void kanaPartsAndKanjiWithTheirReadingsAreLoaded(GameTestHelper helper) {
        JapaneseName expected = new JapaneseName(List.of(
                NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく")));

        helper.assertValueEqual(japaneseNameOf("block.minecraft.oak_log"), Optional.of(expected), "oak log");
        helper.succeed();
    }

    @GameTest
    public void kanjiReadTogetherAreOnePart(GameTestHelper helper) {
        JapaneseName expected = new JapaneseName(List.of(new NamePart("松明", "たいまつ")));

        helper.assertValueEqual(japaneseNameOf("block.minecraft.torch"), Optional.of(expected), "torch");
        helper.succeed();
    }

    /**
     * Parses a name list that should be refused, and checks that the error says which entry is wrong. The JSON is
     * written with single quotes, so it reads more easily here.
     */
    private static void assertRefusedNaming(GameTestHelper helper, String json, String expectedInMessage) {
        String nameList = json.replace('\'', '"');
        try {
            NameListFile.parse(JsonParser.parseString(nameList));
        } catch (IllegalArgumentException e) {
            helper.assertTrue(e.getMessage().contains(expectedInMessage),
                    "the error \"" + e.getMessage() + "\" names " + expectedInMessage);
            return;
        }
        helper.fail("the name list " + nameList + " was accepted");
    }

    @GameTest
    public void anInvalidEntryIsRefusedWithItsId(GameTestHelper helper) {
        String oakLog = "{'id': 'block.minecraft.oak_log', 'translationKeys': ['block.minecraft.oak_log'], ";
        String id = "block.minecraft.oak_log";

        assertRefusedNaming(helper, "[" + oakLog + "'japaneseName': [['原', 'ゲン']]}]", id);
        assertRefusedNaming(helper, "[" + oakLog + "'japaneseName': [['原']]}]", id);
        assertRefusedNaming(helper, "[" + oakLog + "'japaneseName': []}]", id);
        assertRefusedNaming(helper, "[" + oakLog + "'japaneseName': 'オークの原木'}]", id);
        assertRefusedNaming(helper, "[" + oakLog + "'reading': 'おーくのげんぼく'}]", id);
        helper.succeed();
    }

    @GameTest
    public void anEntryWithoutAnIdIsRefusedWithItsPlace(GameTestHelper helper) {
        String dirt = "{'id': 'block.minecraft.dirt', 'translationKeys': ['block.minecraft.dirt'], "
                + "'japaneseName': [['土', 'つち']]}";
        String place = "entry 1 without an id";

        assertRefusedNaming(helper, "[" + dirt + ", {'translationKeys': ['block.minecraft.sand']}]", place);
        assertRefusedNaming(helper, "[" + dirt + ", 'block.minecraft.sand']", place);
        helper.succeed();
    }
}
