package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import io.github.willdalh.oboecraft.fabric.review.Trigger;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Clicks in menus on the server: containers, the crafting grid, armor slots and the recipe book. */
public class MenuGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final ItemId OAK_PLANKS = new ItemId("block.minecraft.oak_planks");
    private static final ItemId CRAFTING_TABLE = new ItemId("block.minecraft.crafting_table");
    /** The first slot of the main inventory, in the inventory and as the first player slot of a menu. */
    private static final int INVENTORY_SLOT = 9;
    /** The first player slot of a chest menu with three rows. */
    private static final int CHEST_MENU_INVENTORY_SLOT = 27;
    private static final int PRIMARY = 0;
    private static final int SECONDARY = 1;

    private static ServerReviews reviews() {
        return OboecraftMod.serverReviews();
    }

    private static AbstractContainerMenu openChest(ServerPlayer player, SimpleContainer chest) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> ChestMenu.threeRows(id, inventory, chest), Component.literal("Chest")));
        return player.containerMenu;
    }

    @GameTest
    public void aLockedStackIsTakenFromAChestOnlyAfterAPass(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.OAK_LOG, 4));
        try {
            AbstractContainerMenu menu = openChest(player, chest);
            menu.clicked(0, PRIMARY, ContainerInput.PICKUP, player);
            helper.assertValueEqual(chest.getItem(0).getCount(), 4, "oak logs left in the chest");
            helper.assertTrue(menu.getCarried().isEmpty(), "the player carries the locked oak logs");

            menu.clicked(0, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertValueEqual(chest.getItem(0).getCount(), 4, "oak logs left after a shift-click");

            reviews().start(player, 1, new StackLocation.MenuSlot(menu.containerId, 0));
            helper.assertValueEqual(reviews().activeTrigger(player),
                    Optional.of(new Trigger.Container(menu.containerId)), "trigger");
            reviews().answer(player, 1, "オークの原木");

            menu.clicked(0, PRIMARY, ContainerInput.PICKUP, player);
            helper.assertValueEqual(menu.getCarried().getCount(), 4, "oak logs carried after the pass");
            menu.clicked(1, PRIMARY, ContainerInput.PICKUP, player);
            helper.assertValueEqual(chest.getItem(1).getCount(), 4, "oak logs put back in the chest");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aLockedStackIsntPutInAChest(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        SimpleContainer chest = new SimpleContainer(27);
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.DIRT, 8));
            AbstractContainerMenu menu = openChest(player, chest);

            menu.clicked(CHEST_MENU_INVENTORY_SLOT, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(chest.isEmpty(), "the chest is empty after a shift-click");

            menu.clicked(CHEST_MENU_INVENTORY_SLOT, PRIMARY, ContainerInput.PICKUP, player);
            helper.assertValueEqual(menu.getCarried().getCount(), 8, "dirt carried, since moving it is free");
            menu.clicked(0, PRIMARY, ContainerInput.PICKUP, player);
            helper.assertTrue(chest.isEmpty(), "the chest is empty after putting down the carried dirt");
            helper.assertValueEqual(menu.getCarried().getCount(), 8, "dirt carried");

            reviews().start(player, 1, new StackLocation.Carried(menu.containerId));
            helper.assertValueEqual(reviews().activeItem(player),
                    Optional.of(new ItemId("block.minecraft.dirt")), "item under review");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void clicksAreRefusedDuringAReviewAndClosingTheMenuCancelsIt(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.OAK_LOG, 4));
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.OAK_PLANKS, 8));
            TestProgress.unlock(player, OAK_PLANKS);
            AbstractContainerMenu menu = openChest(player, chest);
            reviews().start(player, 1, new StackLocation.MenuSlot(menu.containerId, 0));

            menu.clicked(CHEST_MENU_INVENTORY_SLOT, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(chest.getItem(1).isEmpty(), "unlocked planks moved during a review");

            player.closeContainer();
            helper.assertValueEqual(reviews().activeItem(player), Optional.empty(), "item under review");
            menu = openChest(player, chest);
            menu.clicked(CHEST_MENU_INVENTORY_SLOT, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertValueEqual(chest.getItem(1).getCount(), 8, "unlocked planks moved after the review");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aReviewOfAStackInTheInventoryIsKeptWhenAnotherMenuCloses(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG));
            reviews().start(player, 1, new StackLocation.InventorySlot(0));
            helper.assertValueEqual(reviews().activeTrigger(player), Optional.of(new Trigger.Inventory()), "trigger");

            openChest(player, new SimpleContainer(27));
            player.closeContainer();
            helper.assertValueEqual(reviews().activeItem(player), Optional.of(OAK_LOG), "item under review");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void lockedIngredientsAndALockedResultArentCrafted(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        InventoryMenu menu = player.inventoryMenu;
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.OAK_PLANKS, 8));
            menu.clicked(INVENTORY_SLOT, PRIMARY, ContainerInput.PICKUP, player);
            menu.clicked(InventoryMenu.CRAFT_SLOT_START, SECONDARY, ContainerInput.PICKUP, player);
            helper.assertTrue(menu.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem().isEmpty(),
                    "locked planks in the crafting grid");

            TestProgress.unlock(player, OAK_PLANKS);
            for (int slot = InventoryMenu.CRAFT_SLOT_START; slot < InventoryMenu.CRAFT_SLOT_END; slot++) {
                menu.clicked(slot, SECONDARY, ContainerInput.PICKUP, player);
            }
            menu.clicked(INVENTORY_SLOT, PRIMARY, ContainerInput.PICKUP, player);
            helper.assertTrue(menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.CRAFTING_TABLE),
                    "a crafting table is the result");

            menu.clicked(InventoryMenu.RESULT_SLOT, PRIMARY, ContainerInput.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty(), "the locked crafting table is taken");
            menu.clicked(InventoryMenu.RESULT_SLOT, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.CRAFTING_TABLE),
                    "the locked crafting table is still the result after a shift-click");

            reviews().start(player, 1, new StackLocation.MenuSlot(menu.containerId, InventoryMenu.RESULT_SLOT));
            helper.assertValueEqual(reviews().activeItem(player), Optional.of(CRAFTING_TABLE), "item under review");
            helper.assertValueEqual(reviews().activeTrigger(player),
                    Optional.of(new Trigger.Crafting(menu.containerId)), "trigger");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void lockedArmorIsntPutOnInTheInventory(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        InventoryMenu menu = player.inventoryMenu;
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.IRON_CHESTPLATE));
            menu.clicked(INVENTORY_SLOT, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "worn after a shift-click");

            menu.clicked(INVENTORY_SLOT, PRIMARY, ContainerInput.PICKUP, player);
            menu.clicked(InventoryMenu.ARMOR_SLOT_START + 1, PRIMARY, ContainerInput.PICKUP, player);
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "worn after putting it down");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aLockedStackCanBeThrownOutOfTheInventoryWhileDroppingIsntAnInteraction(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.DIRT, 8));
            player.inventoryMenu.clicked(INVENTORY_SLOT, PRIMARY, ContainerInput.THROW, player);
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT).getCount(), 7, "dirt left");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void theRecipeBookDoesntMoveLockedIngredients(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        InventoryMenu menu = player.inventoryMenu;
        RecipeHolder<?> craftingTable = helper.getLevel().getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("crafting_table")))
                .orElseThrow();
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.OAK_PLANKS, 8));
            menu.handlePlacement(false, false, craftingTable, helper.getLevel(), player.getInventory());
            helper.assertTrue(menu.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem().isEmpty(),
                    "locked planks moved into the crafting grid");
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT).getCount(), 8, "planks left");

            TestProgress.unlock(player, OAK_PLANKS);
            menu.handlePlacement(false, false, craftingTable, helper.getLevel(), player.getInventory());
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT).getCount(), 4, "planks left");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }
}
