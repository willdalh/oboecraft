package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ResolvableProfile;

/** Checks that Minecraft's item names carry the translation keys the mod maps stacks with. */
public class ItemMappingGameTest {
    private static void assertKey(GameTestHelper helper, ItemStack stack, String expected) {
        Optional<String> key = ItemMapping.translationKeyOf(stack);
        helper.assertValueEqual(key, Optional.of(expected), "translation key of " + stack);
    }

    @GameTest
    public void aBlockMapsToItsBlockKey(GameTestHelper helper) {
        assertKey(helper, new ItemStack(Items.OAK_LOG), "block.minecraft.oak_log");
        helper.succeed();
    }

    @GameTest
    public void aCustomNameIsIgnored(GameTestHelper helper) {
        ItemStack renamed = new ItemStack(Items.OAK_LOG);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Not a log"));

        assertKey(helper, renamed, "block.minecraft.oak_log");
        helper.succeed();
    }

    @GameTest
    public void potionsMapToTheirEffectAndShareItAcrossStrengths(GameTestHelper helper) {
        assertKey(helper, PotionContents.createItemStack(Items.POTION, Potions.HEALING),
                "item.minecraft.potion.effect.healing");
        assertKey(helper, PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING),
                "item.minecraft.potion.effect.healing");
        assertKey(helper, PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.NIGHT_VISION),
                "item.minecraft.tipped_arrow.effect.night_vision");
        helper.succeed();
    }

    @GameTest
    public void aNamedPlayerHeadMapsToItsFormatKey(GameTestHelper helper) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved("Steve"));

        assertKey(helper, head, "block.minecraft.player_head.named");
        helper.succeed();
    }

    @GameTest
    public void aShieldWithABaseColorHasItsOwnKey(GameTestHelper helper) {
        ItemStack shield = new ItemStack(Items.SHIELD);
        shield.set(DataComponents.BASE_COLOR, DyeColor.RED);

        assertKey(helper, shield, "item.minecraft.shield.red");
        helper.succeed();
    }

    @GameTest
    public void aStackInTheNameListIsThatItem(GameTestHelper helper) {
        Optional<ItemId> item =
                OboecraftMod.itemMapping().entryOf(new ItemStack(Items.OAK_LOG, 12)).map(NameListEntry::id);

        helper.assertValueEqual(item, Optional.of(new ItemId("block.minecraft.oak_log")), "item");
        helper.succeed();
    }

    @GameTest
    public void aStackNotInTheNameListIsNotAnItem(GameTestHelper helper) {
        helper.assertTrue(OboecraftMod.itemMapping().entryOf(new ItemStack(Items.ANDESITE)).isEmpty(),
                Component.literal("andesite isn't in the name list"));
        helper.assertTrue(OboecraftMod.itemMapping().entryOf(ItemStack.EMPTY).isEmpty(),
                Component.literal("an empty stack isn't an item"));
        helper.succeed();
    }
}
