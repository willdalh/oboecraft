package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class LessonsGameTest {
    private static final ItemId OAK = new ItemId("block.minecraft.oak_log");

    @GameTest public void lessonActionsAreScopedAndKnownCanBeReverted(GameTestHelper helper) {
        var player = TestPlayers.join(helper, UUID.randomUUID());
        var reviews = OboecraftMod.serverReviews();
        var progress = OboecraftMod.serverProgress();
        try {
            var position = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
            player.snapTo(position.getX(), position.getY(), position.getZ());
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
            reviews.start(player, 10, new StackLocation.InventorySlot(0));
            reviews.action(player, 9, ReviewAction.Action.KNOWN);
            helper.assertValueEqual(progress.state(player, OAK), SrsState.Leveled.NEW, "stale action ignored");
            reviews.action(player, 10, ReviewAction.Action.SHOW_ANSWER);
            reviews.action(player, 10, ReviewAction.Action.DONT_KNOW);
            reviews.answer(player, 10, "wrong");
            helper.assertTrue(reviews.isReviewing(player), "lesson continues");
            helper.assertValueEqual(progress.state(player, OAK), SrsState.Leveled.NEW, "lesson doesn't punish");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 4, "items kept");
            reviews.action(player, 10, ReviewAction.Action.KNOWN);
            helper.assertValueEqual(progress.state(player, OAK), new SrsState.Known(), "known");
            helper.assertFalse(reviews.isReviewing(player), "known ends review");
            reviews.start(player, 11, new StackLocation.InventorySlot(0));
            helper.assertFalse(reviews.isReviewing(player), "known cannot be reviewed");
            reviews.revert(player, OAK);
            helper.assertValueEqual(progress.state(player, OAK), SrsState.Leveled.NEW, "revert");
        } finally { TestPlayers.leave(player); }
        helper.succeed();
    }

    @GameTest public void ignoreDropsStacksAndBlocksPickupUntilReverted(GameTestHelper helper) {
        var player = TestPlayers.join(helper, UUID.randomUUID());
        var reviews = OboecraftMod.serverReviews();
        try {
            var position = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
            player.snapTo(position.getX(), position.getY(), position.getZ());
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
            player.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 2));
            player.getInventory().setItem(2, new ItemStack(Items.DIRT, 3));
            reviews.start(player, 20, new StackLocation.InventorySlot(0));
            reviews.action(player, 20, ReviewAction.Action.IGNORE);
            helper.assertValueEqual(OboecraftMod.serverProgress().state(player, OAK), new SrsState.Ignored(), "ignored");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "first stack dropped");
            helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "second stack dropped");
            helper.assertValueEqual(player.getInventory().getItem(2).getCount(), 3, "other item kept");
            int dropped = player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4)).stream()
                    .filter(entity -> entity.getItem().is(Items.OAK_LOG)).mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertValueEqual(dropped, 6, "items are on the ground, not deleted");
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.OAK_LOG));
            entity.setNoPickUpDelay();
            entity.playerTouch(player);
            helper.assertFalse(entity.isRemoved(), "ignored pickup leaves entity");
            helper.assertFalse(OboecraftMod.locks().mayPickUp(OboecraftMod.serverProgress(), player, entity.getItem()), "pickup denied");
            helper.assertTrue(OboecraftMod.locks().mayPickUp(OboecraftMod.serverProgress(), player, new ItemStack(Items.DIRT)), "new items may be picked up");
            reviews.revert(player, OAK);
            helper.assertTrue(OboecraftMod.locks().mayPickUp(OboecraftMod.serverProgress(), player, entity.getItem()), "reverted item may be picked up");
        } finally { TestPlayers.leave(player); }
        helper.succeed();
    }
    @GameTest public void ignoredContainerStacksStayPutAndCannotStartReviews(GameTestHelper helper) {
        var player = TestPlayers.join(helper, UUID.randomUUID());
        var chest = new net.minecraft.world.SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.OAK_LOG, 4));
        try {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, opener) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inventory, chest),
                    net.minecraft.network.chat.Component.literal("Chest")));
            var menu = player.containerMenu;
            var reviews = OboecraftMod.serverReviews();
            reviews.start(player, 1, new StackLocation.MenuSlot(menu.containerId, 0));
            reviews.action(player, 1, ReviewAction.Action.IGNORE);
            helper.assertValueEqual(chest.getItem(0).getCount(), 4, "ignoring doesn't remove container contents");
            menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
            helper.assertValueEqual(chest.getItem(0).getCount(), 4, "ignored contents cannot be taken");
            helper.assertTrue(menu.getCarried().isEmpty(), "cursor stays empty");
            reviews.start(player, 2, new StackLocation.MenuSlot(menu.containerId, 0));
            helper.assertFalse(reviews.isReviewing(player), "ignored items never start a review");
        } finally { TestPlayers.leave(player); }
        helper.succeed();
    }

    @GameTest public void revertChecksTheSendersCurrentState(GameTestHelper helper) {
        var player = TestPlayers.join(helper, UUID.randomUUID());
        var other = TestPlayers.join(helper, UUID.randomUUID());
        var progress = OboecraftMod.serverProgress();
        var reviews = OboecraftMod.serverReviews();
        try {
            var learning = new SrsState.Leveled(2, java.util.Optional.of(progress.now().plusSeconds(100)));
            progress.update(player, OAK, learning);
            progress.update(other, OAK, new SrsState.Known());
            reviews.revert(player, OAK);
            helper.assertValueEqual(progress.state(player, OAK), learning, "learning state cannot be reset");
            helper.assertValueEqual(progress.state(other, OAK), new SrsState.Known(), "another player's state unchanged");
            progress.update(player, OAK, new SrsState.Leveled(OboecraftMod.serverProgress().settings().topLevel(), java.util.Optional.empty()));
            reviews.revert(player, OAK);
            helper.assertValueEqual(progress.state(player, OAK), SrsState.Leveled.NEW, "mastered state reset");
        } finally { TestPlayers.leave(player); TestPlayers.leave(other); }
        helper.succeed();
    }

    @GameTest public void ignoreDropsPersonalCraftingInputsBeforeClosingInventory(GameTestHelper helper) {
        var player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            var position = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
            player.snapTo(position.getX(), position.getY(), position.getZ());
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 1));
            var crafting = player.inventoryMenu.getCraftSlots();
            crafting.setItem(0, new ItemStack(Items.OAK_LOG, 3));
            crafting.setItem(1, new ItemStack(Items.DIRT, 2));
            var reviews = OboecraftMod.serverReviews();
            reviews.start(player, 1, new StackLocation.InventorySlot(0));
            reviews.action(player, 1, ReviewAction.Action.IGNORE);
            helper.assertTrue(crafting.getItem(0).isEmpty(), "ignored crafting input is dropped");
            helper.assertValueEqual(crafting.getItem(1).getCount(), 2, "other crafting input is preserved");
            player.inventoryMenu.removed(player);
            for (int slot = 0; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; slot++) {
                helper.assertFalse(player.getInventory().getItem(slot).is(Items.OAK_LOG), "no ignored input returns to inventory");
            }
            int dropped = player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4)).stream()
                    .filter(entity -> entity.getItem().is(Items.OAK_LOG)).mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertValueEqual(dropped, 4, "inventory and grid stacks are on the ground");
        } finally { TestPlayers.leave(player); }
        helper.succeed();
    }

}
