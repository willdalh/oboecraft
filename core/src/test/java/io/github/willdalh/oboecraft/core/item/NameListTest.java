package io.github.willdalh.oboecraft.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NameListTest {
    private static final JapaneseName OAK_LOG_NAME = new JapaneseName(List.of(
            NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("原", "げん"), new NamePart("木", "ぼく")));
    private static final NameListEntry OAK_LOG = new NameListEntry(
            new ItemId("block.minecraft.oak_log"), List.of("block.minecraft.oak_log"), OAK_LOG_NAME);
    private static final NameListEntry MUSIC_DISC = new NameListEntry(
            new ItemId("item.minecraft.music_disc_13"),
            List.of("item.minecraft.music_disc_13", "item.minecraft.music_disc_cat"),
            new JapaneseName(List.of(NamePart.kana("レコード"))));
    private static final JapaneseName OAK_WOOD_NAME = new JapaneseName(
            List.of(NamePart.kana("オーク"), NamePart.kana("の"), new NamePart("木", "き")));

    @Test
    void findsAnEntryByEachOfItsTranslationKeys() {
        NameList nameList = new NameList(List.of(OAK_LOG, MUSIC_DISC));

        assertEquals(Optional.of(OAK_LOG), nameList.byTranslationKey("block.minecraft.oak_log"));
        assertEquals(Optional.of(MUSIC_DISC), nameList.byTranslationKey("item.minecraft.music_disc_13"));
        assertEquals(Optional.of(MUSIC_DISC), nameList.byTranslationKey("item.minecraft.music_disc_cat"));
    }

    @Test
    void keepsTheEntriesInOrder() {
        assertEquals(List.of(MUSIC_DISC, OAK_LOG), new NameList(List.of(MUSIC_DISC, OAK_LOG)).entries());
    }

    @Test
    void findsAnEntryById() {
        NameList nameList = new NameList(List.of(OAK_LOG, MUSIC_DISC));

        assertEquals(Optional.of(MUSIC_DISC), nameList.byId(new ItemId("item.minecraft.music_disc_13")));
    }

    @Test
    void aKeyOrIdNotInTheListHasNoEntry() {
        NameList nameList = new NameList(List.of(OAK_LOG));

        assertTrue(nameList.byTranslationKey("block.minecraft.andesite").isEmpty());
        assertTrue(nameList.byId(new ItemId("block.minecraft.andesite")).isEmpty());
    }

    @Test
    void rejectsTwoEntriesWithTheSameId() {
        NameListEntry otherOakLog = new NameListEntry(OAK_LOG.id(), List.of("block.minecraft.oak_wood"), OAK_WOOD_NAME);

        assertThrows(IllegalArgumentException.class, () -> new NameList(List.of(OAK_LOG, otherOakLog)));
    }

    @Test
    void rejectsATranslationKeyInTwoEntries() {
        NameListEntry oakWood = new NameListEntry(
                new ItemId("block.minecraft.oak_wood"), List.of("block.minecraft.oak_log"), OAK_WOOD_NAME);

        assertThrows(IllegalArgumentException.class, () -> new NameList(List.of(OAK_LOG, oakWood)));
    }

    @Test
    void anEntryNeedsATranslationKey() {
        ItemId id = OAK_LOG.id();

        assertThrows(IllegalArgumentException.class, () -> new NameListEntry(id, List.of(), OAK_LOG_NAME));
        assertThrows(IllegalArgumentException.class, () -> new NameListEntry(id, List.of(" "), OAK_LOG_NAME));
    }

    @Test
    void anItemIdCantBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> new ItemId(" "));
    }
}
