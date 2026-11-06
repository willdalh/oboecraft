package io.github.willdalh.oboecraft.fabric.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** An anvil renames only name tags. Repairing still works, and other items keep their names. */
public class AnvilGameTest {
    private static AnvilMenu openAnvil(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> new AnvilMenu(id, inventory), Component.literal("Anvil")));
        return (AnvilMenu) player.containerMenu;
    }

    private static ItemStack result(AnvilMenu anvil) {
        return anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
    }

    @GameTest
    public void aNameTagIsRenamed(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        player.experienceLevel = 10;
        try {
            AnvilMenu anvil = openAnvil(player);
            anvil.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.NAME_TAG));
            anvil.setItemName("ポチ");
            helper.assertValueEqual(result(anvil).get(DataComponents.CUSTOM_NAME), Component.literal("ポチ"),
                    "the renamed name tag's name");
            helper.assertTrue(anvil.getCost() > 0, "renaming a name tag costs levels");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void anotherItemIsNotRenamed(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        player.experienceLevel = 10;
        try {
            AnvilMenu anvil = openAnvil(player);
            anvil.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
            anvil.setItemName("Sting");
            helper.assertTrue(result(anvil).isEmpty(), "an anvil with only a sword to rename has no result");
            helper.assertValueEqual(anvil.getCost(), 0, "cost");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void anotherItemKeepsItsCustomName(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        player.experienceLevel = 10;
        try {
            AnvilMenu anvil = openAnvil(player);
            ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Sting"));
            anvil.getSlot(AnvilMenu.INPUT_SLOT).set(named);
            anvil.setItemName("");
            helper.assertTrue(result(anvil).isEmpty(), "an anvil doesn't take a sword's custom name away");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void repairingStillWorksWithoutRenaming(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        player.experienceLevel = 30;
        try {
            AnvilMenu anvil = openAnvil(player);
            ItemStack damaged = new ItemStack(Items.DIAMOND_SWORD);
            damaged.setDamageValue(1000);
            anvil.getSlot(AnvilMenu.INPUT_SLOT).set(damaged);
            anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND));
            anvil.setItemName("Sting");
            ItemStack repaired = result(anvil);
            helper.assertTrue(repaired.is(Items.DIAMOND_SWORD) && repaired.getDamageValue() < 1000,
                    "the sword is repaired");
            helper.assertFalse(repaired.has(DataComponents.CUSTOM_NAME), "the repaired sword isn't renamed");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void renamingGoesByTheMinecraftItemNotItsItemName(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        player.experienceLevel = 10;
        try {
            AnvilMenu anvil = openAnvil(player);
            ItemStack swordNamedLikeANameTag = new ItemStack(Items.DIAMOND_SWORD);
            swordNamedLikeANameTag.set(DataComponents.ITEM_NAME, Component.translatable("item.minecraft.name_tag"));
            anvil.getSlot(AnvilMenu.INPUT_SLOT).set(swordNamedLikeANameTag);
            anvil.setItemName("Sting");
            helper.assertTrue(result(anvil).isEmpty(), "a sword with a name tag's item name isn't renamed");

            ItemStack nameTagWithItsOwnName = new ItemStack(Items.NAME_TAG);
            nameTagWithItsOwnName.set(DataComponents.ITEM_NAME, Component.literal("Special Tag"));
            anvil.getSlot(AnvilMenu.INPUT_SLOT).set(nameTagWithItsOwnName);
            anvil.setItemName("ポチ");
            helper.assertValueEqual(result(anvil).get(DataComponents.CUSTOM_NAME), Component.literal("ポチ"),
                    "a name tag with its own item name is renamed");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }
}
