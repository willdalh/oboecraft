package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.core.punishment.ProtectedItems;
import io.github.willdalh.oboecraft.core.punishment.Recipe;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.CheckedStack;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.lock.StoppedStack;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import io.github.willdalh.oboecraft.fabric.progress.ServerProgress;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import io.github.willdalh.oboecraft.fabric.review.Trigger;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Fails punish the item on the server: they remove part of the current view, demote the item and ban it. */
public class PunishmentGameTest {
    private static final ItemId OAK_PLANKS = new ItemId("block.minecraft.oak_planks");
    private static final ItemId DIAMOND = new ItemId("item.minecraft.diamond");
    private static final ItemId TORCH = new ItemId("block.minecraft.torch");
    private static final ItemId SHULKER_BOX = new ItemId("block.minecraft.shulker_box");
    private static final Instant START = Instant.parse("2026-11-06T12:00:00Z");

    private static ServerReviews reviews() {
        return OboecraftMod.serverReviews();
    }

    private static ServerProgress progress() {
        return OboecraftMod.serverProgress();
    }

    /** Puts the item at the level, locked since {@link #START}. */
    private static void lockAt(ServerPlayer player, ItemId item, int level) {
        progress().update(player, item, new SrsState.Leveled(level, Optional.of(START)));
    }

    private static int countInInventory(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    @GameTest
    public void twoWrongAnswersRemoveHalfAtLevelOneDemoteAndBan(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        OboecraftMod.clock().setSource(InstantSource.fixed(START));
        try {
            player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 30));
            player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 20));
            lockAt(player, OAK_PLANKS, 1);
            StackLocation slot = new StackLocation.InventorySlot(0);

            // A wrong first answer is saved, so closing the menu and starting again leaves one attempt.
            reviews().start(player, 1, slot);
            reviews().answer(player, 1, "オークの減材");
            helper.assertValueEqual(progress().state(player, OAK_PLANKS),
                    new SrsState.Leveled(1, Optional.of(START), false, 1, Optional.empty()), "one wrong attempt");
            helper.assertTrue(reviews().isReviewing(player), "the review goes on after one wrong answer");
            reviews().close(player, 1);
            reviews().start(player, 2, slot);
            helper.assertTrue(reviews().isReviewing(player), "the review starts again");

            // The second wrong answer fails: half of the 50 planks are removed, and the item is demoted and banned.
            reviews().answer(player, 2, "wrong");
            helper.assertFalse(reviews().isReviewing(player), "a fail ends the review");
            helper.assertValueEqual(countInInventory(player, Items.OAK_PLANKS), 25, "planks left");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 5, "taken from the first stack first");
            helper.assertValueEqual(progress().state(player, OAK_PLANKS), new SrsState.Leveled(
                    1, Optional.of(START), true, 0, Optional.of(START.plus(Duration.ofMinutes(5)))), "failed state");

            // While banned, no review starts and the lock check stops interactions without the answer menu.
            reviews().start(player, 3, slot);
            helper.assertFalse(reviews().isReviewing(player), "a banned item can't be reviewed");
            Optional<LockDecision> decision = OboecraftMod.locks().firstStopped(progress(), player, List.of(
                    new CheckedStack(player.getInventory().getItem(0), Interaction.PLACE, slot)))
                    .map(StoppedStack::decision);
            helper.assertValueEqual(decision, Optional.of(LockDecision.BANNED), "placing a banned item");

            // Once the ban is over, a pass unlocks it at its demoted level without promoting it.
            Instant later = START.plus(Duration.ofMinutes(5));
            OboecraftMod.clock().setSource(InstantSource.fixed(later));
            reviews().start(player, 4, slot);
            helper.assertTrue(reviews().isReviewing(player), "the review starts after the ban");
            reviews().answer(player, 4, "オークの板材");
            helper.assertValueEqual(progress().state(player, OAK_PLANKS), new SrsState.Leveled(1,
                    Optional.of(later.plus(Duration.ofMinutes(10))), false, 0, Optional.of(later)), "passed state");
        } finally {
            OboecraftMod.clock().setSource(InstantSource.system());
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aFailInAContainerRemovesFromTheContainerAndInventoryCombined(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        OboecraftMod.clock().setSource(InstantSource.fixed(START));
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 30));
        try {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 20));
            player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DIAMOND, 10));
            player.getInventory().setItem(36, new ItemStack(Items.DIAMOND, 1));
            lockAt(player, DIAMOND, 8);
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, opener) -> ChestMenu.threeRows(id, inventory, chest), Component.literal("Chest")));
            int containerId = player.containerMenu.containerId;

            reviews().start(player, 1, new StackLocation.MenuSlot(containerId, 0));
            helper.assertValueEqual(ActiveReviews.trigger(player), Optional.of(new Trigger.Container(containerId)),
                    "container trigger");
            reviews().action(player, 1, ReviewAction.Action.DONT_KNOW);

            // Level 8 removes 15% of the 60 diamonds in view, 9, from the chest first. Worn armor slots don't count.
            helper.assertValueEqual(chest.getItem(0).getCount(), 21, "diamonds left in the chest");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 20, "diamonds left in the hotbar");
            helper.assertValueEqual(player.getInventory().getItem(36).getCount(), 1, "the armor slot is in use");
            helper.assertValueEqual(((SrsState.Leveled) progress().state(player, DIAMOND)).level(), 6,
                    "level 8 is demoted by two");
            helper.assertFalse(reviews().isReviewing(player), "Don't know fails at once");
        } finally {
            OboecraftMod.clock().setSource(InstantSource.system());
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aFailOnACraftedItemRemovesTheIngredientsByTheirPartOfTheRecipe(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        OboecraftMod.clock().setSource(InstantSource.fixed(START));
        InventoryMenu menu = player.inventoryMenu;
        try {
            player.getInventory().setItem(0, new ItemStack(Items.COAL, 39));
            player.getInventory().setItem(1, new ItemStack(Items.STICK, 19));
            menu.getSlot(InventoryMenu.CRAFT_SLOT_START).set(new ItemStack(Items.COAL, 1));
            menu.getSlot(InventoryMenu.CRAFT_SLOT_START + 2).set(new ItemStack(Items.STICK, 1));
            helper.assertTrue(menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.TORCH), "torches");
            lockAt(player, TORCH, 1);

            reviews().start(player, 1, new StackLocation.MenuSlot(menu.containerId, InventoryMenu.RESULT_SLOT));
            helper.assertValueEqual(ActiveReviews.trigger(player), Optional.of(new Trigger.Crafting(menu.containerId,
                    new Recipe(Map.of(new ItemId("item.minecraft.coal"), 1, new ItemId("item.minecraft.stick"), 1),
                            2))), "the recipe is noted");
            reviews().action(player, 1, ReviewAction.Action.DONT_KNOW);

            // Each ingredient is half of the recipe, so 25% of the 40 coal and 20 sticks are removed, the grid first.
            helper.assertTrue(menu.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem().isEmpty(), "coal in the grid");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 30, "coal left in the inventory");
            helper.assertTrue(menu.getSlot(InventoryMenu.CRAFT_SLOT_START + 2).getItem().isEmpty(), "stick in grid");
            helper.assertValueEqual(player.getInventory().getItem(1).getCount(), 15, "sticks left in the inventory");
            helper.assertTrue(menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().isEmpty(), "no torches any more");
            helper.assertTrue(((SrsState.Leveled) progress().state(player, TORCH)).demoted(), "the torch is demoted");
        } finally {
            OboecraftMod.clock().setSource(InstantSource.system());
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aProtectedItemIsNeverRemovedButStillDemotedAndBanned(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        OboecraftMod.clock().setSource(InstantSource.fixed(START));
        try {
            player.getInventory().setItem(0, new ItemStack(Items.SHULKER_BOX, 1));
            lockAt(player, SHULKER_BOX, 2);

            reviews().start(player, 1, new StackLocation.InventorySlot(0));
            reviews().action(player, 1, ReviewAction.Action.DONT_KNOW);

            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 1, "the shulker box is kept");
            helper.assertValueEqual(progress().state(player, SHULKER_BOX), new SrsState.Leveled(
                    1, Optional.of(START), true, 0, Optional.of(START.plus(Duration.ofMinutes(5)))), "failed state");
        } finally {
            OboecraftMod.clock().setSource(InstantSource.system());
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void everyProtectedItemIsInTheNameList(GameTestHelper helper) {
        for (ItemId item : ProtectedItems.IDS) {
            helper.assertTrue(OboecraftMod.itemMappings().get(LearningLanguage.JAPANESE).nameList().byId(item).isPresent(), "in the name list: " + item);
        }
        helper.succeed();
    }
}
