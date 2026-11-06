package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.fabric.lock.MenuSlots;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.StonecutterMenu;

/** What putting a stack into a menu's slot and taking it out does, for Minecraft's own menus. */
public class MenuSlotsGameTest {
    private static void assertSlot(GameTestHelper helper, AbstractContainerMenu menu, int slot,
            Optional<Interaction> putting, Optional<Interaction> taking) {
        String what = menu.getClass().getSimpleName() + " slot " + slot;
        helper.assertValueEqual(MenuSlots.putting(menu, menu.getSlot(slot)), putting, "putting into " + what);
        helper.assertValueEqual(MenuSlots.taking(menu, menu.getSlot(slot)), taking, "taking from " + what);
    }

    private static Optional<Interaction> is(Interaction interaction) {
        return Optional.of(interaction);
    }

    @GameTest
    public void slotsOfMinecraftsMenus(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            Inventory inventory = player.getInventory();
            Optional<Interaction> free = Optional.empty();
            Optional<Interaction> taken = is(Interaction.TAKE_FROM_CONTAINER);
            Optional<Interaction> ingredient = is(Interaction.INGREDIENT);
            Optional<Interaction> crafted = is(Interaction.CRAFT);

            ChestMenu chest = ChestMenu.threeRows(1, inventory);
            assertSlot(helper, chest, 0, is(Interaction.PUT_IN_CONTAINER), taken);
            assertSlot(helper, chest, 27, free, free);

            InventoryMenu inventoryMenu = player.inventoryMenu;
            assertSlot(helper, inventoryMenu, InventoryMenu.RESULT_SLOT, ingredient, crafted);
            assertSlot(helper, inventoryMenu, InventoryMenu.CRAFT_SLOT_START, ingredient, taken);
            assertSlot(helper, inventoryMenu, InventoryMenu.ARMOR_SLOT_START, is(Interaction.EQUIP), free);
            assertSlot(helper, inventoryMenu, InventoryMenu.SHIELD_SLOT, free, free);

            CraftingMenu crafting = new CraftingMenu(1, inventory);
            assertSlot(helper, crafting, 0, ingredient, crafted);
            assertSlot(helper, crafting, 1, ingredient, taken);

            FurnaceMenu furnace = new FurnaceMenu(1, inventory);
            assertSlot(helper, furnace, 0, is(Interaction.PUT_IN_CONTAINER), taken);
            assertSlot(helper, furnace, 1, is(Interaction.FUEL), taken);
            assertSlot(helper, furnace, 2, is(Interaction.PUT_IN_CONTAINER), taken);

            BrewingStandMenu brewing = new BrewingStandMenu(1, inventory);
            assertSlot(helper, brewing, 3, is(Interaction.PUT_IN_CONTAINER), taken);
            assertSlot(helper, brewing, 4, is(Interaction.FUEL), taken);

            StonecutterMenu stonecutter = new StonecutterMenu(1, inventory);
            assertSlot(helper, stonecutter, StonecutterMenu.INPUT_SLOT, ingredient, taken);
            assertSlot(helper, stonecutter, StonecutterMenu.RESULT_SLOT, ingredient, crafted);

            LoomMenu loom = new LoomMenu(1, inventory);
            assertSlot(helper, loom, loom.getResultSlot().index, ingredient, crafted);

            MerchantMenu merchant = new MerchantMenu(1, inventory);
            assertSlot(helper, merchant, 0, is(Interaction.TRADE), taken);
            assertSlot(helper, merchant, 2, is(Interaction.TRADE), is(Interaction.TRADE));
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aMountsSaddleAndArmorArentPartOfTheCurrentView(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            var horse = helper.spawn(EntityTypes.HORSE, new BlockPos(1, 2, 1));
            var menu = new HorseInventoryMenu(1, player.getInventory(),
                    new SimpleContainer(AbstractMountInventoryMenu.getInventorySize(0)), horse, 0);
            for (int slot = 0; slot < 2; slot++) {
                helper.assertTrue(menu.getSlot(slot) instanceof ArmorSlot,
                        "slot " + slot + " is the mount's saddle or armor");
                helper.assertFalse(MenuSlots.isInCurrentView(menu, menu.getSlot(slot)),
                        "the mount's saddle and armor are worn, not in the current view");
            }
            ChestMenu chest = ChestMenu.threeRows(1, player.getInventory());
            helper.assertTrue(MenuSlots.isInCurrentView(chest, chest.getSlot(0)), "a chest's slot is in the current view");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }
}
