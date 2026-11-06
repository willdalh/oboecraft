package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
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
}
