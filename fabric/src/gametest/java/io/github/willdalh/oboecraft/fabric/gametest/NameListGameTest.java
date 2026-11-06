package io.github.willdalh.oboecraft.fabric.gametest;

import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.item.NameListFile;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.ResolvableProfile;

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
     * Every stack the game can name is an item: each Minecraft item, and the stacks the game's code names from their
     * components, which the name list tool keeps a hand-written list of. Names that the game's data gives stacks are
     * read from the data by the tool itself.
     */
    @GameTest
    public void everyStackTheGameNamesIsInTheNameList(GameTestHelper helper) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                stacks.add(new ItemStack(item));
            }
        }
        for (Holder<Potion> potion : BuiltInRegistries.POTION.listElements().toList()) {
            for (Item item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
                stacks.add(PotionContents.createItemStack(item, potion));
            }
        }
        for (DyeColor color : DyeColor.values()) {
            ItemStack shield = new ItemStack(Items.SHIELD);
            shield.set(DataComponents.BASE_COLOR, color);
            stacks.add(shield);
        }
        ItemStack lodestoneCompass = new ItemStack(Items.COMPASS);
        lodestoneCompass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.empty(), true));
        stacks.add(lodestoneCompass);
        ItemStack namedHead = new ItemStack(Items.PLAYER_HEAD);
        namedHead.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved("Steve"));
        stacks.add(namedHead);
        stacks.add(Raid.getOminousBannerInstance(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));

        ItemMapping mapping = OboecraftMod.itemMapping();
        List<String> missing = stacks.stream()
                .filter(stack -> mapping.entryOf(stack).isEmpty())
                .map(stack -> ItemMapping.translationKeyOf(stack).orElse(stack.toString()))
                .distinct()
                .toList();
        helper.assertTrue(missing.isEmpty(), "every stack is in the name list, but these keys aren't: " + missing);
        helper.succeed();
    }

    @GameTest
    public void keysWithTheSameJapaneseNameAreOneItem(GameTestHelper helper) {
        ItemMapping mapping = OboecraftMod.itemMapping();
        Optional<ItemId> disc13 = mapping.entryOf(new ItemStack(Items.MUSIC_DISC_13)).map(NameListEntry::id);
        Optional<ItemId> discCat = mapping.entryOf(new ItemStack(Items.MUSIC_DISC_CAT)).map(NameListEntry::id);

        helper.assertValueEqual(discCat, disc13, "the item of the music discs");
        helper.succeed();
    }

    @GameTest
    public void aNamedPlayerHeadIsThePlayerHead(GameTestHelper helper) {
        ItemStack namedHead = new ItemStack(Items.PLAYER_HEAD);
        namedHead.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved("Steve"));

        helper.assertValueEqual(OboecraftMod.itemMapping().entryOf(namedHead).map(NameListEntry::id),
                Optional.of(new ItemId("block.minecraft.player_head")), "the item of a named player head");
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
