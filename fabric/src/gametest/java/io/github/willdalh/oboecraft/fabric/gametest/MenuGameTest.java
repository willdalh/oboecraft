package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.punishment.Recipe;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import io.github.willdalh.oboecraft.fabric.review.Trigger;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.ClientSideMerchant;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Clicks in menus on the server: containers, the crafting grid, armor slots and the recipe book. */
public class MenuGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final ItemId OAK_PLANKS = new ItemId("block.minecraft.oak_planks");
    private static final ItemId CRAFTING_TABLE = new ItemId("block.minecraft.crafting_table");
    private static final ItemId BIRCH_PLANKS = new ItemId("block.minecraft.birch_planks");
    private static final ItemId EMERALD = new ItemId("item.minecraft.emerald");
    /** The first slot of the main inventory, in the inventory and as the first player slot of a menu. */
    private static final int INVENTORY_SLOT = 9;
    /** The first player slot of a chest menu with three rows. */
    private static final int CHEST_MENU_INVENTORY_SLOT = 27;
    /** The first player slot of a furnace menu. */
    private static final int FURNACE_MENU_INVENTORY_SLOT = 3;
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
            helper.assertValueEqual(ActiveReviews.trigger(player),
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
            helper.assertValueEqual(ActiveReviews.item(player),
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
            helper.assertValueEqual(ActiveReviews.item(player), Optional.empty(), "item under review");
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
            helper.assertValueEqual(ActiveReviews.trigger(player), Optional.of(new Trigger.Inventory()), "trigger");

            openChest(player, new SimpleContainer(27));
            player.closeContainer();
            helper.assertValueEqual(ActiveReviews.item(player), Optional.of(OAK_LOG), "item under review");
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
            helper.assertValueEqual(ActiveReviews.item(player), Optional.of(CRAFTING_TABLE), "item under review");
            helper.assertValueEqual(ActiveReviews.trigger(player),
                    Optional.of(new Trigger.Crafting(menu.containerId, new Recipe(Map.of(OAK_PLANKS, 4), 4))),
                    "trigger with the recipe noted");
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
    public void shiftClickingLockedStacksWithinTheInventoryIsFree(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        InventoryMenu menu = player.inventoryMenu;
        try {
            player.getInventory().setItem(0, new ItemStack(Items.DIRT, 8));
            menu.clicked(InventoryMenu.USE_ROW_SLOT_START, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "locked dirt left in the hotbar");
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT).getCount(), 8,
                    "locked dirt moved into the main inventory");

            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            menu.clicked(InventoryMenu.ARMOR_SLOT_START + 1, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "locked armor taken off");
            helper.assertValueEqual(ActiveReviews.item(player), Optional.empty(), "item under review");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void shiftClickingInAFurnaceChecksWhereTheStackMoves(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.DIRT, 8));
            player.getInventory().setItem(INVENTORY_SLOT + 1, new ItemStack(Items.COAL, 8));
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, opener) -> new FurnaceMenu(id, inventory), Component.literal("Furnace")));
            AbstractContainerMenu menu = player.containerMenu;

            // Dirt can't be smelted or burned, so the furnace moves it within the inventory, which is free.
            menu.clicked(FURNACE_MENU_INVENTORY_SLOT, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(player.getInventory().getItem(INVENTORY_SLOT).isEmpty(), "locked dirt moved");

            // Coal goes into the fuel slot, which is checked.
            menu.clicked(FURNACE_MENU_INVENTORY_SLOT + 1, PRIMARY, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "locked coal in the fuel slot");
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT + 1).getCount(), 8, "coal left");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void choosingATradeDoesntMoveALockedPayment(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.EMERALD, 8));
            ClientSideMerchant merchant = new ClientSideMerchant(player);
            MerchantOffers offers = new MerchantOffers();
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD), 10, 0, 0));
            merchant.overrideOffers(offers);
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, opener) -> new MerchantMenu(id, inventory, merchant), Component.literal("Trader")));
            MerchantMenu menu = (MerchantMenu) player.containerMenu;

            menu.tryMoveItems(0);
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "locked emeralds in the payment slot");
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT).getCount(), 8, "emeralds left");

            TestProgress.unlock(player, EMERALD);
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG));
            reviews().start(player, 1, new StackLocation.InventorySlot(0));
            player.connection.handleSelectTrade(new ServerboundSelectTradePacket(0));
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "unlocked emeralds moved during a review");

            reviews().close(player, 1);
            player.connection.handleSelectTrade(new ServerboundSelectTradePacket(0));
            helper.assertValueEqual(menu.getSlot(0).getItem().getCount(), 8, "unlocked emeralds moved after it");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void droppingIsRefusedDuringAReview(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
            reviews().start(player, 1, new StackLocation.InventorySlot(0));

            player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS, BlockPos.ZERO, Direction.DOWN));
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 4, "oak logs during a review");

            reviews().close(player, 1);
            player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS, BlockPos.ZERO, Direction.DOWN));
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "oak logs dropped after the review");
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
    public void theRecipeBookUsesOnlyUnlockedIngredients(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        InventoryMenu menu = player.inventoryMenu;
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.OAK_PLANKS, 8));
            placeRecipe(helper, player, "crafting_table");
            helper.assertTrue(menu.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem().isEmpty(),
                    "locked planks moved into the crafting grid");
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT).getCount(), 8, "planks left");

            // Unlocked birch planks do just as well.
            player.getInventory().setItem(INVENTORY_SLOT + 1, new ItemStack(Items.BIRCH_PLANKS, 8));
            TestProgress.unlock(player, BIRCH_PLANKS);
            placeRecipe(helper, player, "crafting_table");
            helper.assertTrue(menu.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem().is(Items.BIRCH_PLANKS),
                    "unlocked birch planks moved into the crafting grid");
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT).getCount(), 8, "oak planks left");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void theRecipeBookPlacesNothingWhenOneIngredientIsLocked(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.OAK_PLANKS, 8));
            player.getInventory().setItem(INVENTORY_SLOT + 1, new ItemStack(Items.STICK, 8));
            TestProgress.unlock(player, OAK_PLANKS);
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, opener) -> new CraftingMenu(id, inventory), Component.literal("Crafting")));
            placeRecipe(helper, player, "wooden_pickaxe");

            for (int slot = 1; slot <= 9; slot++) {
                helper.assertTrue(player.containerMenu.getSlot(slot).getItem().isEmpty(),
                        "the crafting grid is empty, not half filled");
            }
            helper.assertValueEqual(player.getInventory().getItem(INVENTORY_SLOT).getCount(), 8, "planks left");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    /** Places a recipe from the recipe book into the player's open menu, as the server does it. */
    private static void placeRecipe(GameTestHelper helper, ServerPlayer player, String recipe) {
        RecipeHolder<?> holder = helper.getLevel().getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(recipe)))
                .orElseThrow();
        ((RecipeBookMenu) player.containerMenu)
                .handlePlacement(false, false, holder, helper.getLevel(), player.getInventory());
    }
}
