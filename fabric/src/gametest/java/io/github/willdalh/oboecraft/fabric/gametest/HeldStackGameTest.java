package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Actions with a held stack on the server: using, eating, putting on armor, shooting, attacking, mining, dropping and
 * using a stack on an entity. Placing is in {@link PlacingGameTest}.
 */
public class HeldStackGameTest {
    private static final ItemId BREAD = new ItemId("item.minecraft.bread");
    private static final ItemId IRON_CHESTPLATE = new ItemId("item.minecraft.iron_chestplate");
    private static final ItemId BOW = new ItemId("item.minecraft.bow");
    private static final ItemId ARROW = new ItemId("item.minecraft.arrow");
    private static final ItemId IRON_SWORD = new ItemId("item.minecraft.iron_sword");
    private static final ItemId IRON_PICKAXE = new ItemId("item.minecraft.iron_pickaxe");

    private static ServerPlayer playerHolding(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        Vec3 standingSpot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        player.snapTo(standingSpot.x(), standingSpot.y(), standingSpot.z());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static InteractionResult use(GameTestHelper helper, ServerPlayer player) {
        return player.gameMode.useItem(
                player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    @GameTest
    public void aLockedFoodIsntEatenUntilItIsUnlocked(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.BREAD, 3));
        try {
            player.getFoodData().setFoodLevel(10);

            helper.assertTrue(use(helper, player) instanceof InteractionResult.Fail, "using locked bread fails");
            helper.assertFalse(player.isUsingItem(), "the player is eating locked bread");

            TestProgress.unlock(player, BREAD);
            helper.assertTrue(use(helper, player).consumesAction(), "using unlocked bread works");
            helper.assertTrue(player.isUsingItem(), "the player eats unlocked bread");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aLockedArmorIsntPutOnUntilItIsUnlocked(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.IRON_CHESTPLATE));
        try {
            helper.assertTrue(use(helper, player) instanceof InteractionResult.Fail, "using a locked chestplate fails");
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "the locked chestplate is worn");

            TestProgress.unlock(player, IRON_CHESTPLATE);
            use(helper, player);
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "the unlocked chestplate is worn");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aBowDoesntShootLockedArrowsAndTheirReviewIsForTheirSlot(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.BOW));
        ServerReviews reviews = OboecraftMod.serverReviews();
        try {
            player.getInventory().setItem(5, new ItemStack(Items.ARROW, 16));
            TestProgress.unlock(player, BOW);

            helper.assertTrue(use(helper, player) instanceof InteractionResult.Fail,
                    "drawing a bow with locked arrows fails");
            helper.assertFalse(player.isUsingItem(), "the player draws the bow");
            reviews.start(player, 1, new StackLocation.InventorySlot(5));
            helper.assertValueEqual(reviews.activeItem(player), Optional.of(ARROW), "item under review");
            reviews.close(player, 1);

            TestProgress.unlock(player, ARROW);
            helper.assertTrue(use(helper, player).consumesAction(), "drawing a bow with unlocked arrows works");
            helper.assertTrue(player.isUsingItem(), "the player draws the bow");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aLockedWeaponDoesntHitButAStackThatIsntAWeaponDoes(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.IRON_SWORD));
        Zombie first = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        Zombie second = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 0));
        Zombie third = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        try {
            player.attack(first);
            helper.assertValueEqual(first.getHealth(), first.getMaxHealth(), "health after a hit with a locked sword");

            // A block in hand isn't a weapon, so hitting with it isn't checked.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            player.attack(second);
            helper.assertTrue(second.getHealth() < second.getMaxHealth(), "hitting while holding locked dirt works");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            TestProgress.unlock(player, IRON_SWORD);
            player.attack(third);
            helper.assertTrue(third.getHealth() < third.getMaxHealth(), "hitting with an unlocked sword works");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aLockedToolDoesntMineButAStackThatIsntAToolDoes(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            helper.setBlock(new BlockPos(x, 0, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.TORCH);
        }
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.IRON_PICKAXE));
        try {
            startMining(helper, player, new BlockPos(0, 1, 3));
            helper.assertBlockPresent(Blocks.TORCH, new BlockPos(0, 1, 3));

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            startMining(helper, player, new BlockPos(1, 1, 3));
            helper.assertBlockNotPresent(Blocks.TORCH, new BlockPos(1, 1, 3));

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            TestProgress.unlock(player, IRON_PICKAXE);
            startMining(helper, player, new BlockPos(2, 1, 3));
            helper.assertBlockNotPresent(Blocks.TORCH, new BlockPos(2, 1, 3));
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    /** Starts breaking a block, which breaks a torch at once, as the server does it. */
    private static void startMining(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        player.gameMode.handleBlockBreakAction(helper.absolutePos(pos),
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, helper.getLevel().getMaxY(), 0);
    }

    @GameTest
    public void aLockedStackCanBeDroppedWhileDroppingIsntAnInteraction(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.DIRT, 4));
        try {
            player.drop(false);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "dirt left after dropping one");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aLockedStackIsntUsedOnAnEntity(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.WHEAT));
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(2, 1, 1));
        try {
            helper.assertTrue(useOn(helper, player, cow) instanceof InteractionResult.Fail,
                    "using locked wheat on a cow fails");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertValueEqual(useOn(helper, player, cow), InteractionResult.PASS,
                    "using an empty hand on a cow");
        } finally {
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    private static InteractionResult useOn(GameTestHelper helper, ServerPlayer player, Cow cow) {
        return UseEntityCallback.EVENT.invoker().interact(
                player, helper.getLevel(), InteractionHand.MAIN_HAND, cow, null);
    }
}
