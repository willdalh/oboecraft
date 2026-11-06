package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NameListGeneratorTest {
    /** Guesses every name, which the tests write in kana, as one part, and notes that it guessed. */
    private final List<String> guessed = new ArrayList<>();
    private final NameGuesser guesser = name -> {
        guessed.add(name);
        return new GuessedName(new JapaneseName(List.of(NamePart.kana(name))), List.of("guessed"));
    };

    private static final JapaneseName REVIEWED_OAK_LOG = new JapaneseName(List.of(
            NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく")));

    private static ItemNameKey key(String translationKey, String item) {
        return new ItemNameKey(translationKey, item);
    }

    private static Map<String, String> japanese(String... keysAndNames) {
        Map<String, String> japanese = new LinkedHashMap<>();
        for (int i = 0; i < keysAndNames.length; i += 2) {
            japanese.put(keysAndNames[i], keysAndNames[i + 1]);
        }
        return japanese;
    }

    private static NameListEntry entry(String id, List<String> keys, JapaneseName name) {
        return new NameListEntry(new ItemId(id), keys, name);
    }

    @Test
    void keysWithTheSameNameAreOneEntryWithTheFirstKeyAsItsId() {
        List<ItemNameKey> keys = List.of(
                key("item.minecraft.music_disc_13", "minecraft:music_disc_13"),
                key("item.minecraft.music_disc_cat", "minecraft:music_disc_cat"),
                key("item.minecraft.compass", "minecraft:compass"));
        Map<String, String> japanese = japanese(
                "item.minecraft.music_disc_13", "レコード",
                "item.minecraft.music_disc_cat", "レコード",
                "item.minecraft.compass", "コンパス");

        NameListGenerator.Result result = NameListGenerator.generate(keys, japanese, List.of(), guesser);

        assertEquals(2, result.entries().size());
        NameListEntry discs = result.entries().getFirst();
        assertEquals(new ItemId("item.minecraft.music_disc_13"), discs.id());
        assertEquals(List.of("item.minecraft.music_disc_13", "item.minecraft.music_disc_cat"), discs.translationKeys());
        assertEquals(List.of("レコード", "コンパス"), guessed);
        assertEquals(2, result.generated().size());
    }

    @Test
    void aReviewedEntryWithTheSameNameIsKept() {
        NameListEntry oakLog = entry("block.minecraft.oak_log", List.of("block.minecraft.oak_log"), REVIEWED_OAK_LOG);

        NameListGenerator.Result result = NameListGenerator.generate(
                List.of(key("block.minecraft.oak_log", "minecraft:oak_log")),
                japanese("block.minecraft.oak_log", "オークの原木"), List.of(oakLog), guesser);

        assertEquals(List.of(oakLog), result.entries());
        assertTrue(result.generated().isEmpty());
        assertTrue(guessed.isEmpty());
    }

    @Test
    void anEntryWhoseNameChangedKeepsItsIdAndIsGuessedAgain() {
        NameListEntry oakLog = entry("block.minecraft.oak_log", List.of("block.minecraft.oak_log"), REVIEWED_OAK_LOG);

        NameListGenerator.Result result = NameListGenerator.generate(
                List.of(key("block.minecraft.oak_log", "minecraft:oak_log")),
                japanese("block.minecraft.oak_log", "オークのマルタ"), List.of(oakLog), guesser);

        NameListGenerator.GeneratedEntry generated = result.generated().getFirst();
        assertEquals(new ItemId("block.minecraft.oak_log"), generated.entry().id());
        assertEquals("オークのマルタ", generated.entry().japaneseName().text());
        assertEquals("The Japanese name changed from オークの原木", generated.notes().getFirst());
    }

    @Test
    void aKeyThatGetsAnotherNameSplitsOffAsANewItem() {
        NameListEntry discs = entry("item.minecraft.music_disc_13",
                List.of("item.minecraft.music_disc_13", "item.minecraft.music_disc_cat"),
                new JapaneseName(List.of(NamePart.kana("レコード"))));

        NameListGenerator.Result result = NameListGenerator.generate(
                List.of(key("item.minecraft.music_disc_cat", "minecraft:music_disc_cat"),
                        key("item.minecraft.music_disc_13", "minecraft:music_disc_13")),
                japanese("item.minecraft.music_disc_13", "レコード", "item.minecraft.music_disc_cat", "ネコのレコード"),
                List.of(discs), guesser);

        assertEquals(List.of(new ItemId("item.minecraft.music_disc_cat"), new ItemId("item.minecraft.music_disc_13")),
                result.entries().stream().map(NameListEntry::id).toList());
        assertEquals(List.of("ネコのレコード"), guessed);
        assertEquals("Split from item.minecraft.music_disc_13 (レコード), so it is a new item",
                result.generated().getFirst().notes().getFirst());
    }

    @Test
    void aKeyWhoseNameDidntChangeKeepsItsPartsWhenTheOtherKeysSplitOff() {
        JapaneseName reviewed = new JapaneseName(List.of(NamePart.kana("レコード")));
        NameListEntry discs = entry("item.minecraft.music_disc_13",
                List.of("item.minecraft.music_disc_13", "item.minecraft.music_disc_cat"), reviewed);

        NameListGenerator.Result result = NameListGenerator.generate(
                List.of(key("item.minecraft.music_disc_13", "minecraft:music_disc_13"),
                        key("item.minecraft.music_disc_cat", "minecraft:music_disc_cat")),
                japanese("item.minecraft.music_disc_13", "サーティーンのレコード", "item.minecraft.music_disc_cat", "レコード"),
                List.of(discs), guesser);

        NameListEntry cat = result.entries().get(1);
        assertEquals(new ItemId("item.minecraft.music_disc_cat"), cat.id());
        assertSame(reviewed, cat.japaneseName());
        assertEquals(List.of("サーティーンのレコード"), guessed);
        assertTrue(result.notes().getFirst().contains("Split from item.minecraft.music_disc_13"));
    }

    @Test
    void aDroppedIdKeyThatComesBackIsANewItemAndTheEntryKeepsItsId() {
        // The entry's id key was dropped by Minecraft, so only its other key is left.
        NameListEntry stew = entry("item.minecraft.a", List.of("item.minecraft.b"),
                new JapaneseName(List.of(NamePart.kana("シチュー"))));

        NameListGenerator.Result result = NameListGenerator.generate(
                List.of(key("item.minecraft.a", "minecraft:a"), key("item.minecraft.c", "minecraft:a"),
                        key("item.minecraft.b", "minecraft:b")),
                japanese("item.minecraft.a", "スープ", "item.minecraft.c", "スープ", "item.minecraft.b", "シチュー"),
                List.of(stew), guesser);

        assertEquals(List.of(new ItemId("item.minecraft.c"), new ItemId("item.minecraft.a")),
                result.entries().stream().map(NameListEntry::id).toList());
        assertEquals(List.of("item.minecraft.b"), result.entries().get(1).translationKeys());
    }

    @Test
    void aNewItemWhoseOnlyKeyIsAnotherItemsIdIsRefused() {
        NameListEntry stew = entry("item.minecraft.a", List.of("item.minecraft.b"),
                new JapaneseName(List.of(NamePart.kana("シチュー"))));

        assertThrows(IllegalStateException.class, () -> NameListGenerator.generate(
                List.of(key("item.minecraft.a", "minecraft:a"), key("item.minecraft.b", "minecraft:b")),
                japanese("item.minecraft.a", "スープ", "item.minecraft.b", "シチュー"), List.of(stew), guesser));
    }

    @Test
    void twoEntriesThatGetTheSameNameAreRefused() {
        List<NameListEntry> existing = List.of(
                entry("item.minecraft.brick", List.of("item.minecraft.brick"),
                        new JapaneseName(List.of(NamePart.kana("レンガ")))),
                entry("block.minecraft.bricks", List.of("block.minecraft.bricks"),
                        new JapaneseName(List.of(NamePart.kana("レンガブロック")))));

        assertThrows(IllegalStateException.class, () -> NameListGenerator.generate(
                List.of(key("item.minecraft.brick", "minecraft:brick"),
                        key("block.minecraft.bricks", "minecraft:bricks")),
                japanese("item.minecraft.brick", "レンガ", "block.minecraft.bricks", "レンガ"), existing, guesser));
    }

    @Test
    void aKeyNamedAfterAnArgumentIsAddedToItsItem() {
        NameListGenerator.Result result = NameListGenerator.generate(
                List.of(key("block.minecraft.player_head", "minecraft:player_head"),
                        key("block.minecraft.player_head.named", "minecraft:player_head")),
                japanese("block.minecraft.player_head", "プレイヤーのアタマ", "block.minecraft.player_head.named", "%sのアタマ"),
                List.of(), guesser);

        assertEquals(List.of("block.minecraft.player_head", "block.minecraft.player_head.named"),
                result.entries().getFirst().translationKeys());
        assertEquals(List.of("プレイヤーのアタマ"), guessed);
        assertTrue(result.notes().getFirst().contains("is added to block.minecraft.player_head"));
    }

    @Test
    void aKeyNamedAfterAnArgumentForNoItemIsLeftOut() {
        NameListGenerator.Result result = NameListGenerator.generate(
                List.of(key("filled_map.id", null)), japanese("filled_map.id", "ID #%s"), List.of(), guesser);

        assertTrue(result.entries().isEmpty());
        assertTrue(result.notes().getFirst().contains("left out"));
    }

    @Test
    void anEntryNoLongerInTheGameIsKept() {
        NameListEntry oakLog = entry("block.minecraft.oak_log", List.of("block.minecraft.oak_log"), REVIEWED_OAK_LOG);

        NameListGenerator.Result result = NameListGenerator.generate(List.of(), Map.of(), List.of(oakLog), guesser);

        assertEquals(List.of(oakLog), result.entries());
        assertTrue(result.notes().getFirst().contains("no longer in the game"));
    }
}
